package com.flowmind.platform.engine.runtime;

import com.fasterxml.jackson.databind.*;
import com.flowmind.platform.common.api.ErrorCode;
import com.flowmind.platform.common.exception.BusinessException;
import com.flowmind.platform.system.service.SysAuditAndConfigService;
import com.flowmind.platform.system.service.SysAuditAndConfigService.RuntimeModelConfig;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** OpenAI-compatible DeepSeek/Qwen streaming transport; never replays a partially delivered response. */
@Slf4j
@Component
public class LlmClientAdapter implements AutoCloseable {
    public record Completion(String output, int tokens, String provider) {}
    private final ObjectMapper json;
    private final Environment env;
    private final Supplier<Optional<RuntimeModelConfig>> configuration;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().factory());
    @Autowired
    public LlmClientAdapter(ObjectMapper json, Environment env, SysAuditAndConfigService configuration) {
        this(json, env, configuration::configuredRuntimeConfig);
    }
    /** Standalone transport used without Spring or a configuration database. */
    public LlmClientAdapter(ObjectMapper json, Environment env) { this(json, env, Optional::empty); }
    private LlmClientAdapter(ObjectMapper json, Environment env, Supplier<Optional<RuntimeModelConfig>> configuration) {
        this.json = json; this.env = env; this.configuration = configuration;
    }
    @Override @PreDestroy public void close() { deadlines.shutdownNow(); http.close(); }
    private String property(String key, String fallback) { return env.getProperty("flowmind.llm." + key, fallback); }
    public Completion complete(String model, double temperature, String system, String prompt, int budget, Consumer<String> onChunk) {
        // Resolve once per call so live updates cannot mix a new URL with an old key/model.
        var config = configuration.get().orElseGet(() -> environmentConfig(model));
        String provider = config.activeProvider();
        if (!Set.of("DEEPSEEK", "QWEN", "MOCK_LOCAL").contains(provider)) throw new BusinessException(ErrorCode.PARAM_ERROR, "未知模型通道");
        String key = config.apiKey();
        if (key.isBlank() || "MOCK_LOCAL".equals(provider)) return local(system, prompt, budget, onChunk);
        boolean fallback = config.enableMockFallback();
        AtomicBoolean delivered = new AtomicBoolean();
        try {
            return remote(config, temperature, system, prompt, budget, chunk -> { delivered.set(true); onChunk.accept(chunk); });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模型调用已中断");
        } catch (Exception ex) {
            log.warn("Model {} request failed ({})", provider, ex.getClass().getSimpleName());
            if (!fallback || delivered.get()) throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模型流式调用失败，请稍后重试");
            return local(system, prompt, budget, onChunk);
        }
    }
    private RuntimeModelConfig environmentConfig(String model) {
        String provider = model == null || model.isBlank() ? property("active-provider", "DEEPSEEK") : model.toUpperCase(Locale.ROOT);
        String prefix = provider.toLowerCase(Locale.ROOT);
        String base = property(prefix + ".base-url", "QWEN".equals(provider)
            ? "https://dashscope.aliyuncs.com/compatible-mode/v1" : "https://api.deepseek.com/v1");
        String modelName = property(prefix + ".model-name", "QWEN".equals(provider) ? "qwen-plus" : "deepseek-chat");
        return new RuntimeModelConfig(provider, base, property(prefix + ".api-key", env.getProperty(provider + "_API_KEY", "")),
            modelName, Boolean.parseBoolean(property("enable-mock-fallback", "true")));
    }
    private Completion remote(RuntimeModelConfig config, double temperature, String system, String prompt, int budget,
                              Consumer<String> onChunk) throws Exception {
        int promptTokens = estimate(system + prompt);
        if (budget <= promptTokens) throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
        int timeout = Integer.parseInt(property("timeout-seconds", "60"));
        var body = Map.of("model", config.modelName(), "temperature", temperature, "stream", true,
            "stream_options", Map.of("include_usage", true), "max_tokens", Math.min(4096, budget - promptTokens),
            "messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", prompt)));
        var req = HttpRequest.newBuilder(URI.create(config.baseUrl().replaceAll("/+$", "") + "/chat/completions"))
            .timeout(Duration.ofSeconds(timeout)).header("Authorization", "Bearer " + config.apiKey())
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        var response = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream stream = response.body()) {
            if (response.statusCode() / 100 != 2) throw new IOException("Model HTTP " + response.statusCode());
            var expired = new AtomicBoolean();
            var timer = deadlines.schedule(() -> { expired.set(true); try { stream.close(); } catch (IOException ignored) { /* Already closed. */ } }, timeout, TimeUnit.SECONDS);
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                var output = new StringBuilder(); int usage = 0; boolean ended = false;
                for (String line; (line = reader.readLine()) != null;) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]")) { ended = true; break; }
                    if (data.isEmpty()) continue;
                    JsonNode event = json.readTree(data);
                    if (event.has("error")) throw new IOException("Model returned an error");
                    usage = Math.max(usage, event.path("usage").path("total_tokens").asInt());
                    JsonNode choice = event.path("choices").path(0);
                    String chunk = choice.path("delta").path("content").asText("");
                    if (!chunk.isEmpty()) { output.append(chunk); onChunk.accept(chunk); }
                    if (!choice.path("finish_reason").isMissingNode() && !choice.path("finish_reason").isNull()) ended = true;
                    if (output.length() > 200000) throw new IOException("Model output exceeded limit");
                }
                if (expired.get() || !ended || output.isEmpty()) throw new IOException("Incomplete model stream");
                return new Completion(output.toString(), usage > 0 ? usage : estimate(system + prompt + output), config.activeProvider());
            } finally { timer.cancel(false); }
        }
    }
    public static int estimate(String value) { return Math.max(1, (value.length() + 1) / 2); }
    private Completion local(String system, String prompt, int budget, Consumer<String> onChunk) {
        String topic = (system + " " + prompt).toLowerCase(Locale.ROOT);
        String excerpt = prompt.replaceAll("\\s+", " ").trim();
        excerpt = excerpt.substring(0, Math.min(180, excerpt.length()));
        String report;
        if (topic.contains("sql") || topic.contains("漏洞") || topic.contains("安全")) {
            var risks = new ArrayList<String>();
            if (topic.contains("sql") || topic.contains("execute") || topic.contains("拼接")) risks.add("SQL 拼接可能引入注入风险；使用 PreparedStatement 和参数绑定，禁止直接拼接用户输入。");
            if (topic.contains("null") || topic.contains("空指针")) risks.add("访问对象前校验空值，明确可空边界与异常处理。");
            if (risks.isEmpty()) risks.add("检查输入校验、访问权限及敏感信息输出，结合具体代码逐项验证。");
            report = "### 安全审查（本地沙箱）\n\n输入摘要：" + excerpt + "\n\n" + String.join("\n", risks)
                + "\n\n### 验证建议\n补充正常输入、边界值和恶意输入测试，确认修复后的行为。";
        } else if (topic.contains("测试") || topic.contains("代码")) {
            report = "### 代码与测试建议（本地沙箱）\n\n需求摘要：" + excerpt
                + "\n\n1. 明确输入类型、输出结构与异常分支。\n2. 为正常路径、空值、边界值设计独立测试。\n3. 使用断言验证结果，记录失败原因并执行回归测试。";
        } else if (topic.contains("周报")) {
            report = "### 周报草稿（本地沙箱）\n\n本周事项：" + excerpt
                + "\n\n- 完成情况：按输入事项逐项补充产出与验证结果。\n- 风险与问题：列出阻塞、责任人和解决日期。\n- 下周计划：优先完成遗留事项，再推进下一阶段目标。";
        } else {
            report = "### 需求分析（本地沙箱）\n\n输入摘要：" + excerpt
                + "\n\n建议步骤：明确目标与约束，整理可验证的事实，逐步执行并检查结果。\n\n此结果由本地规则根据输入生成；需要开放式推理时可配置 DeepSeek 或 Qwen。";
        }
        int tokens = estimate(system + prompt + report);
        if (tokens > budget) throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
        long delay = Long.parseLong(property("mock-chunk-delay-ms", Arrays.asList(env.getActiveProfiles()).contains("test") ? "1" : "25"));
        for (int i = 0; i < report.length();) {
            int end = Math.min(report.length(), i + 8);
            if (end < report.length() && Character.isHighSurrogate(report.charAt(end - 1))) end--;
            onChunk.accept(report.substring(i, end)); i = end;
            if (delay > 0) try { Thread.sleep(delay); } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); throw new BusinessException(ErrorCode.INTERNAL_ERROR, "模型调用已中断");
            }
        }
        return new Completion(report, tokens, "MOCK_LOCAL");
    }
}
