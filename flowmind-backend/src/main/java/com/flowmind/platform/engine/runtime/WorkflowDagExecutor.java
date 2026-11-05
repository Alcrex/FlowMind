package com.flowmind.platform.engine.runtime;

import com.fasterxml.jackson.databind.*;
import com.flowmind.platform.common.api.*;
import com.flowmind.platform.common.exception.BusinessException;
import com.flowmind.platform.common.security.LoginUser;
import com.flowmind.platform.engine.entity.*;
import com.flowmind.platform.engine.mapper.*;
import com.flowmind.platform.engine.vo.ExecRecordDetailVO;
import com.flowmind.platform.workflow.entity.WfNodeEdge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowDagExecutor {
    public record ExecutionPlan(Long workflowId, String wfName, String mode, Map<String, Object> input,
                                List<WfNodeEdge> nodes, List<WfNodeEdge> edges) {}
    private final WfExecRecordMapper records;
    private final WfExecNodeLogMapper logs;
    private final EngineAccountMapper accounts;
    private final LlmClientAdapter llm;
    private final KnowledgeBase knowledge;
    private final ObjectMapper json;
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.]+)\\s*\\}\\}");

    /** Account row lock serializes billing for this user; record, logs and debit commit together. */
    @Transactional(rollbackFor = Exception.class)
    public ExecRecordDetailVO execute(ExecutionPlan plan, LoginUser user, BiConsumer<String, Object> emit) {
        Integer quota = accounts.lockQuota(user.id());
        if (quota == null) throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        if (quota <= 0) throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
        long start = System.nanoTime();
        var record = new WfExecRecord(); record.setExecNo("EXEC-" + UUID.randomUUID());
        record.setWorkflowId(plan.workflowId()); record.setTriggerUserId(user.id()); record.setTriggerMode(plan.mode());
        record.setInputPayload(plan.input()); record.setExecStatus("RUNNING"); record.setTotalTokens(0); record.setDurationMs(0);
        record.setStartedAt(LocalDateTime.now()); records.insert(record); record.setWfName(plan.wfName());
        emit.accept("WORKFLOW_START", Map.of("execRecordId", record.getId(), "execNo", record.getExecNo()));
        Map<String, Object> context = new LinkedHashMap<>(); context.put("input", new LinkedHashMap<>(plan.input()));
        Map<String, List<WfNodeEdge>> incoming = new HashMap<>();
        for (var edge : plan.edges()) incoming.computeIfAbsent(edge.getTargetKey(), k -> new ArrayList<>()).add(edge);
        Set<String> successful = new HashSet<>(); Map<String, Boolean> branches = new HashMap<>();
        List<WfExecNodeLog> detail = new ArrayList<>(); Map<String, Object> finalOutput = new LinkedHashMap<>();
        boolean failed = false; int total = 0;
        for (var node : plan.nodes()) {
            long nodeStart = System.nanoTime(); var row = new WfExecNodeLog(); row.setExecRecordId(record.getId());
            row.setStepOrder(detail.size() + 1); row.setNodeKey(node.getElementKey());
            row.setNodeName(node.getLabelName() == null ? node.getElementKey() : node.getLabelName()); row.setNodeType(node.getNodeType());
            row.setTokenCost(0); row.setCostMs(0); row.setCreatedAt(LocalDateTime.now());
            List<WfNodeEdge> parents = incoming.getOrDefault(node.getElementKey(), List.of());
            // An OR join remains active when at least one incoming path survived pruning.
            boolean active = "INPUT".equals(node.getNodeType()) || parents.stream().anyMatch(e -> successful.contains(e.getSourceKey())
                && (!branches.containsKey(e.getSourceKey()) || Objects.equals(e.getSourceHandle(), branches.get(e.getSourceKey()).toString())));
            if (failed || !active) {
                row.setStepStatus("SKIPPED"); row.setNodeOutput(failed ? "前序节点执行失败" : "条件分支未激活");
                logs.insert(row); detail.add(row); emit.accept("NODE_SKIPPED", row); continue;
            }
            row.setStepStatus("RUNNING"); emit.accept("NODE_START", Map.of("nodeKey", node.getElementKey(), "nodeType", node.getNodeType()));
            Map<String, Object> cfg = node.getConfigJson() == null ? Map.of() : node.getConfigJson();
            String modelProvider = null;
            try {
                String previous = parents.stream().filter(e -> successful.contains(e.getSourceKey()) &&
                    (!branches.containsKey(e.getSourceKey()) || Objects.equals(e.getSourceHandle(), branches.get(e.getSourceKey()).toString())))
                    .map(e -> text(resolve(e.getSourceKey() + ".output", context))).reduce((a, b) -> a + "\n" + b).orElse("");
                String expression = string(cfg, "expression", "");
                String input = switch (node.getNodeType()) {
                    case "INPUT" -> text(plan.input());
                    case "KNOWLEDGE" -> interpolate(string(cfg, "queryTemplate", previous), context);
                    case "LLM" -> interpolate(string(cfg, "userPromptTemplate", previous), context);
                    case "CONDITION" -> interpolate(string(cfg, "fieldExpr", previous), context);
                    case "OUTPUT" -> interpolate(string(cfg, "resultTemplate", previous), context);
                    default -> interpolate(string(cfg, "inputTemplate", string(cfg, "inputExpr", "REGEX_EXTRACT".equals(string(cfg,"ruleType","TRIM")) ? previous : expression.isBlank() ? previous : expression)), context);
                };
                row.setNodeInput(input);
                Map<String, Object> result = new LinkedHashMap<>();
                var handled = NodeHandlers.run(node.getNodeType(), new NodeHandlers.Context(
                    this, knowledge, llm, json, plan, cfg, input, expression, context, quota, total, node.getElementKey(),
                    chunk -> emit.accept("NODE_TOKEN", Map.of("nodeKey", node.getElementKey(), "chunk", chunk))));
                String output = handled.output();
                result.putAll(handled.fields());
                if (handled.modelProvider() != null) modelProvider = handled.modelProvider();
                if (handled.branch() != null) branches.put(node.getElementKey(), handled.branch());
                row.setNodeInput(handled.nodeInput() == null ? input : handled.nodeInput());
                row.setTokenCost(handled.tokenCost());
                if ("OUTPUT".equals(node.getNodeType())) {
                    if (result.containsKey("value")) finalOutput.put(node.getElementKey(), result.get("value"));
                    else finalOutput.put(node.getElementKey(), output);
                    finalOutput.put("report", output);
                }
                result.put("output", output); context.put(node.getElementKey(), result); successful.add(node.getElementKey());
                row.setNodeOutput(output); row.setStepStatus("SUCCESS"); total += row.getTokenCost();
            } catch (Exception ex) {
                failed = true; row.setStepStatus("ERROR"); row.setNodeOutput(ex instanceof BusinessException ? ex.getMessage() : "节点配置或输入处理失败");
                record.setErrorSummary((row.getNodeName() + "：" + row.getNodeOutput()).substring(0, Math.min(500, row.getNodeName().length() + 1 + row.getNodeOutput().length())));
                log.warn("Execution {} node {} failed: {}", record.getExecNo(), node.getElementKey(), ex.getClass().getSimpleName());
            }
            // Persistence failures must roll back the aggregate, rather than become a node-level error.
            if (modelProvider != null && "SUCCESS".equals(row.getStepStatus()))
                accounts.audit(user.id(), user.username(), modelProvider, row.getTokenCost(), elapsed(nodeStart),
                    text(Map.of("workflowId", plan.workflowId(), "execNo", record.getExecNo(), "nodeKey", node.getElementKey())));
            row.setCostMs(elapsed(nodeStart)); logs.insert(row); detail.add(row);
            emit.accept("ERROR".equals(row.getStepStatus()) ? "NODE_ERROR" : "NODE_COMPLETE", row);
        }
        if (!failed && finalOutput.isEmpty()) { failed = true; record.setErrorSummary("没有激活的 OUTPUT 节点，请检查条件分支连线"); }
        if (total > 0 && accounts.debit(user.id(), total) != 1) throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
        record.setExecStatus(failed ? "FAILED" : "SUCCESS"); record.setFinalOutput(finalOutput); record.setTotalTokens(total);
        record.setDurationMs(elapsed(start)); record.setFinishedAt(LocalDateTime.now()); records.updateById(record);
        log.info("Execution {} finished {}: {} tokens", record.getExecNo(), record.getExecStatus(), total);
        return new ExecRecordDetailVO(record, detail);
    }
    void validateInputType(String type, Object value, String name) {
        boolean valid = switch (type.toLowerCase(Locale.ROOT)) {
            case "string", "text" -> value instanceof String;
            case "number", "integer" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "object", "json" -> value instanceof Map || value instanceof List;
            default -> false;
        };
        if (!valid) throw new BusinessException(ErrorCode.PARAM_ERROR, "输入变量类型无效：" + name);
    }
    public String interpolate(String template, Map<String, Object> context) {
        var matcher = VARIABLE.matcher(template == null ? "" : template); var result = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(result, Matcher.quoteReplacement(text(resolve(matcher.group(1), context))));
        matcher.appendTail(result); return result.toString();
    }
    private Object resolve(String path, Map<String, Object> context) {
        Object value = context;
        for (String part : path.split("\\.")) {
            if (value instanceof Map<?,?> map) value = map.get(part);
            else if (value instanceof List<?> list && part.matches("[0-9]{1,6}")) { int index = Integer.parseInt(part); value = index < list.size() ? list.get(index) : null; }
            else return null;
        }
        return value;
    }
    String text(Object value) {
        if (value == null) return "";
        if (value instanceof String s) return s;
        try { return json.writeValueAsString(value); } catch (Exception ex) { throw new BusinessException(ErrorCode.PARAM_ERROR, "无法序列化节点数据"); }
    }
    private static String string(Map<String,Object> cfg, String key, String fallback) { Object value = cfg.get(key); return value == null ? fallback : value.toString(); }
    private static int intValue(Map<String,Object> cfg, String key, int fallback) { return Integer.parseInt(string(cfg, key, Integer.toString(fallback))); }
    private static int elapsed(long start) { return (int)Math.min(Integer.MAX_VALUE, (System.nanoTime() - start) / 1_000_000); }
    String transform(String type, String input, String expression) throws Exception {
        return switch (type) {
            case "TRIM" -> input.strip();
            case "LENGTH" -> Integer.toString(input.length());
            case "JSON_FORMAT" -> json.writerWithDefaultPrettyPrinter().writeValueAsString(json.readTree(input));
            case "REGEX_EXTRACT" -> {
                // Safe subset: no grouping, alternation, backreferences or nested repetitions (no arbitrary scripts).
                if (expression.length() > 200 || input.length() > 4096 || expression.matches(".*[()|{}].*")
                    || expression.contains("\\1") || expression.chars().filter(c -> c == '+' || c == '*' || c == '?').count() > 1)
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "正则提取仅支持安全字符类和简单量词");
                Matcher match = Pattern.compile(expression).matcher(input); yield match.find() ? match.group() : "";
            }
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的文本处理规则");
        };
    }
}
