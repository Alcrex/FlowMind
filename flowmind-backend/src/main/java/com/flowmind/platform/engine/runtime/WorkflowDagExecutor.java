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
                Map<String, Object> result = new LinkedHashMap<>(); String output;
                switch (node.getNodeType()) {
                    case "INPUT" -> {
                        result.putAll(plan.input());
                        JsonNode variables = json.valueToTree(cfg.getOrDefault("variables", List.of()));
                        for (var v : variables) {
                            String name = v.path("name").asText();
                            if (name.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "输入变量名称不能为空");
                            Object value = result.get(name);
                            if (value == null && v.hasNonNull("defaultValue")) value = json.convertValue(v.get("defaultValue"), Object.class);
                            if (v.path("required").asBoolean() && (value == null || value.toString().isBlank())) throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少必填输入：" + name);
                            if (value != null) validateInputType(v.path("type").asText("string"), value, name);
                            result.put(name, value);
                        }
                        @SuppressWarnings("unchecked") var initial = (Map<String,Object>) context.get("input"); initial.putAll(result);
                        output = text(result);
                    }
                    case "KNOWLEDGE" -> {
                        if (quota - total < 64) throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
                        output = retrieve(string(cfg, "knowledgeBase", "ALI_JAVA_SPEC"), input, intValue(cfg, "topK", 3)); row.setTokenCost(64);
                    }
                    case "CODE" -> output = transform(string(cfg, "ruleType", "TRIM"), input, expression);
                    case "CONDITION" -> {
                        String target = interpolate(string(cfg, "targetValue", ""), context);
                        boolean branch = switch (string(cfg, "operator", "CONTAINS")) {
                            case "CONTAINS" -> input.contains(target);
                            case "EQUALS" -> input.equals(target);
                            case "LENGTH_GT" -> input.length() > Integer.parseInt(target);
                            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的条件操作符");
                        };
                        branches.put(node.getElementKey(), branch); output = Boolean.toString(branch); result.put("result", branch);
                    }
                    case "LLM" -> {
                        String system = interpolate(string(cfg, "systemPrompt", "你是一个专业、严谨的助手。"), context);
                        double temperature = Double.parseDouble(string(cfg, "temperature", "0.7"));
                        if (!Double.isFinite(temperature) || temperature < 0 || temperature > 2) throw new BusinessException(ErrorCode.PARAM_ERROR, "模型温度范围为 0~2");
                        row.setNodeInput(system + "\n" + input);
                        var completion = llm.complete(string(cfg, "model", ""), temperature, system, input, quota - total,
                            chunk -> emit.accept("NODE_TOKEN", Map.of("nodeKey", node.getElementKey(), "chunk", chunk)));
                        if (completion.tokens() > quota - total) throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
                        output = completion.output(); row.setTokenCost(completion.tokens());
                        modelProvider = completion.provider();
                    }
                    case "OUTPUT" -> {
                        if ("JSON".equals(string(cfg, "outputFormat", "MARKDOWN"))) {
                            JsonNode value = json.readTree(input);
                            if (value == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "JSON 输出不能为空");
                            Object parsed = json.convertValue(value, Object.class); result.put("value", parsed); finalOutput.put(node.getElementKey(), parsed);
                        } else finalOutput.put(node.getElementKey(), input);
                        output = input; finalOutput.put("report", input);
                    }
                    default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的节点类型");
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
    private void validateInputType(String type, Object value, String name) {
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
    private String text(Object value) {
        if (value == null) return "";
        if (value instanceof String s) return s;
        try { return json.writeValueAsString(value); } catch (Exception ex) { throw new BusinessException(ErrorCode.PARAM_ERROR, "无法序列化节点数据"); }
    }
    private static String string(Map<String,Object> cfg, String key, String fallback) { Object value = cfg.get(key); return value == null ? fallback : value.toString(); }
    private static int intValue(Map<String,Object> cfg, String key, int fallback) { return Integer.parseInt(string(cfg, key, Integer.toString(fallback))); }
    private static int elapsed(long start) { return (int)Math.min(Integer.MAX_VALUE, (System.nanoTime() - start) / 1_000_000); }
    private String transform(String type, String input, String expression) throws Exception {
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
    private record Article(String base, String keywords, String content) {}
    private static final List<Article> KNOWLEDGE = List.of(
        new Article("ALI_JAVA_SPEC", "java sql execute 注入 拼接 数据库", "【阿里 Java 规约】SQL 操作使用 PreparedStatement 预编译参数绑定，禁止拼接外部输入。"),
        new Article("ALI_JAVA_SPEC", "java null 空指针 对象", "【阿里 Java 规约】明确参数可空性，访问对象前校验空值，集合返回空集合而非 null。"),
        new Article("ALI_JAVA_SPEC", "java 异常 日志 资源", "【阿里 Java 规约】使用 try-with-resources 关闭资源；记录异常上下文，禁止吞掉异常。"),
        new Article("OWASP", "安全 sql xss 注入 输入", "【OWASP】对输入进行类型和长度校验，使用参数化查询与输出编码，防止注入和 XSS。"),
        new Article("OWASP", "安全 权限 token 密码", "【OWASP】最小权限访问，服务端验证每个资源的归属，密码采用加盐强哈希。"),
        new Article("SPRING_BOOT", "spring boot 事务 数据库", "【Spring Boot 最佳实践】主从写入使用事务，异常统一处理，DTO 使用 Jakarta Validation。"),
        new Article("SPRING_BOOT", "spring boot 接口 测试", "【Spring Boot 最佳实践】通过依赖注入管理组件，以集成测试覆盖接口契约和事务回滚。"),
        new Article("VUE3", "vue 前端 组件 状态 typescript", "【Vue 3 工程规范】使用 script setup 与 TypeScript；清理组件监听与订阅，统一封装 API。"),
        new Article("VUE3", "vue xss 安全 html", "【Vue 3 工程规范】展示不可信文本时使用文本插值；避免直接注入未经清洗的 HTML。")
    );
    private String retrieve(String base, String query, int topK) {
        if (topK < 1 || topK > 10) throw new BusinessException(ErrorCode.PARAM_ERROR, "topK 范围为 1~10");
        String normalized = switch (base) { case "OWASP_WEB" -> "OWASP"; case "SPRING_BOOT_BEST" -> "SPRING_BOOT"; case "VUE3_SPEC" -> "VUE3"; default -> base; };
        String lower = query.toLowerCase(Locale.ROOT);
        var selected = KNOWLEDGE.stream().filter(a -> a.base().equals(normalized) || "ALL".equals(normalized))
            .sorted(Comparator.<Article>comparingLong(a -> Arrays.stream(a.keywords().split(" ")).filter(lower::contains).count()).reversed())
            .limit(topK).map(Article::content).toList();
        if (selected.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR, "知识库不存在：" + base);
        return String.join("\n", selected);
    }
}
