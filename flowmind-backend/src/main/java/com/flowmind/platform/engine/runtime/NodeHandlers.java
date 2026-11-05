package com.flowmind.platform.engine.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmind.platform.common.api.ErrorCode;
import com.flowmind.platform.common.exception.BusinessException;
import java.util.*;
import java.util.function.Consumer;

/** One handler per canvas node type. Ordering, skipping and billing stay in the executor. */
final class NodeHandlers {
    private NodeHandlers() {}
    record Context(WorkflowDagExecutor executor, KnowledgeBase knowledge, LlmClientAdapter llm, ObjectMapper json,
                   WorkflowDagExecutor.ExecutionPlan plan, Map<String, Object> config, String input, String expression,
                   Map<String, Object> variables, int quota, int used, String nodeKey, Consumer<String> onChunk) {}
    record Result(String output, Map<String, Object> fields, String nodeInput, int tokenCost, String modelProvider, Boolean branch) {}

    static Result run(String type, Context ctx) throws Exception {
        return switch (type) {
            case "INPUT" -> input(ctx);
            case "KNOWLEDGE" -> knowledge(ctx);
            case "CODE" -> new Result(ctx.executor().transform(str(ctx, "ruleType", "TRIM"), ctx.input(), ctx.expression()), Map.of(), null, 0, null, null);
            case "CONDITION" -> condition(ctx);
            case "LLM" -> llm(ctx);
            case "OUTPUT" -> output(ctx);
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的节点类型");
        };
    }

    private static Result input(Context ctx) {
        var fields = new LinkedHashMap<String, Object>();
        fields.putAll(ctx.plan().input());
        JsonNode variables = ctx.json().valueToTree(ctx.config().getOrDefault("variables", List.of()));
        for (var v : variables) {
            String name = v.path("name").asText();
            if (name.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "输入变量名称不能为空");
            Object value = fields.get(name);
            if (value == null && v.hasNonNull("defaultValue")) value = ctx.json().convertValue(v.get("defaultValue"), Object.class);
            if (v.path("required").asBoolean() && (value == null || value.toString().isBlank())) throw new BusinessException(ErrorCode.PARAM_ERROR, "缺少必填输入：" + name);
            if (value != null) ctx.executor().validateInputType(v.path("type").asText("string"), value, name);
            fields.put(name, value);
        }
        @SuppressWarnings("unchecked") var initial = (Map<String, Object>) ctx.variables().get("input");
        initial.putAll(fields);
        return new Result(ctx.executor().text(fields), fields, null, 0, null, null);
    }

    private static Result knowledge(Context ctx) {
        if (ctx.quota() - ctx.used() < 64) throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
        String output = ctx.knowledge().retrieve(str(ctx, "knowledgeBase", "ALI_JAVA_SPEC"), ctx.input(), intValue(ctx, "topK", 3));
        return new Result(output, Map.of(), null, 64, null, null);
    }

    private static Result condition(Context ctx) {
        String target = ctx.executor().interpolate(str(ctx, "targetValue", ""), ctx.variables());
        boolean branch = switch (str(ctx, "operator", "CONTAINS")) {
            case "CONTAINS" -> ctx.input().contains(target);
            case "EQUALS" -> ctx.input().equals(target);
            case "LENGTH_GT" -> ctx.input().length() > Integer.parseInt(target);
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的条件操作符");
        };
        return new Result(Boolean.toString(branch), Map.of("result", branch), null, 0, null, branch);
    }

    private static Result llm(Context ctx) {
        String system = ctx.executor().interpolate(str(ctx, "systemPrompt", "你是一个专业、严谨的助手。"), ctx.variables());
        double temperature = Double.parseDouble(str(ctx, "temperature", "0.7"));
        if (!Double.isFinite(temperature) || temperature < 0 || temperature > 2) throw new BusinessException(ErrorCode.PARAM_ERROR, "模型温度范围为 0~2");
        var completion = ctx.llm().complete(str(ctx, "model", ""), temperature, system, ctx.input(), ctx.quota() - ctx.used(), ctx.onChunk());
        if (completion.tokens() > ctx.quota() - ctx.used()) throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
        return new Result(completion.output(), Map.of(), system + "\n" + ctx.input(), completion.tokens(), completion.provider(), null);
    }

    private static Result output(Context ctx) throws Exception {
        if (!"JSON".equals(str(ctx, "outputFormat", "MARKDOWN"))) return new Result(ctx.input(), Map.of(), null, 0, null, null);
        JsonNode value = ctx.json().readTree(ctx.input());
        if (value == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "JSON 输出不能为空");
        Object parsed = ctx.json().convertValue(value, Object.class);
        return new Result(ctx.input(), Map.of("value", parsed), null, 0, null, null);
    }

    private static String str(Context ctx, String key, String fallback) {
        Object value = ctx.config().get(key);
        return value == null ? fallback : value.toString();
    }
    private static int intValue(Context ctx, String key, int fallback) { return Integer.parseInt(str(ctx, key, Integer.toString(fallback))); }
}
