package com.flowmind.platform.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowmind.platform.common.api.*;
import com.flowmind.platform.common.security.LoginUser;
import com.flowmind.platform.system.dto.*;
import com.flowmind.platform.system.entity.SysModelAuditLog;
import com.flowmind.platform.system.mapper.SysModelAuditLogMapper;
import com.flowmind.platform.system.vo.BigScreenStatsVO;
import com.flowmind.platform.system.vo.BigScreenStatsVO.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class SysAuditAndConfigService {
    private final SysModelAuditLogMapper mapper;
    private final ObjectMapper json;
    private final SecretKeySpec encryptionKey;

    public SysAuditAndConfigService(SysModelAuditLogMapper mapper, ObjectMapper json,
            @Value("${flowmind.jwt.secret}") String secret) {
        this.mapper = mapper;
        this.json = json;
        try { encryptionKey = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)), "AES"); }
        catch (GeneralSecurityException ex) { throw new IllegalStateException("Cannot initialize configuration encryption", ex); }
    }

    /** Filters/login record rejected requests outside business transactions; admin changes and their audits commit together. */
    @Transactional(rollbackFor = Exception.class)
    public void record(LoginUser user, String module, String action, int status, HttpServletRequest req, Object detail, String error) {
        mapper.insert(event(user, module, action, status, req, json.valueToTree(detail), error));
        log.info("Security audit action={} module={} userId={} status={}", action, module, user == null ? null : user.id(), status);
    }

    private SysModelAuditLog event(LoginUser user, String module, String action, int status,
            HttpServletRequest req, JsonNode detail, String error) {
        var item = new SysModelAuditLog();
        if (user != null) { item.setUserId(user.id()); item.setUsername(user.username()); }
        item.setModuleName(module); item.setActionType(action); item.setHttpStatus(status);
        item.setCostTokens(0); item.setDurationMs(0); item.setCreatedAt(LocalDateTime.now());
        if (req != null) { item.setRequestUri(req.getRequestURI()); item.setRequestMethod(req.getMethod()); item.setRequestIp(req.getRemoteAddr()); }
        item.setDetailJson(detail); item.setErrorMsg(error);
        return item;
    }

    public PageResult<SysModelAuditLog> query(AuditLogQueryDTO q) {
        var w = new LambdaQueryWrapper<SysModelAuditLog>()
            .like(hasText(q.getUsername()), SysModelAuditLog::getUsername, q.getUsername())
            .eq(hasText(q.getModuleName()), SysModelAuditLog::getModuleName, q.getModuleName())
            .eq(hasText(q.getActionType()), SysModelAuditLog::getActionType, q.getActionType())
            .eq(hasText(q.getModelProvider()), SysModelAuditLog::getModelProvider, q.getModelProvider())
            .eq(q.getHttpStatus() != null, SysModelAuditLog::getHttpStatus, q.getHttpStatus())
            .ge(q.getStartTime() != null, SysModelAuditLog::getCreatedAt, q.getStartTime())
            .le(q.getEndTime() != null, SysModelAuditLog::getCreatedAt, q.getEndTime())
            .orderByDesc(SysModelAuditLog::getCreatedAt, SysModelAuditLog::getId);
        var page = mapper.selectPage(new Page<>(q.getPageNum(), q.getPageSize()), w);
        // Stored config contains encrypted key material; even ciphertext is excluded from the audit API.
        page.getRecords().forEach(item -> item.setDetailJson(redact(item.getDetailJson())));
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    private JsonNode redact(JsonNode node) {
        if (node == null) return null;
        if (node.isObject()) {
            ObjectNode copy = json.createObjectNode();
            node.fields().forEachRemaining(e -> {
                if (!e.getKey().toLowerCase(Locale.ROOT).contains("apikey")) copy.set(e.getKey(), redact(e.getValue()));
            });
            return copy;
        }
        if (node.isArray()) { var copy = json.createArrayNode(); node.forEach(n -> copy.add(redact(n))); return copy; }
        return node.deepCopy();
    }

    // The agreed schema has no configuration table. Versioned MODEL_CONFIG audit events persist the snapshot.
    private StoredConfig storedConfig() {
        var rows = mapper.selectList(new LambdaQueryWrapper<SysModelAuditLog>()
            .eq(SysModelAuditLog::getModuleName, "ADMIN").eq(SysModelAuditLog::getActionType, "MODEL_CONFIG")
            .orderByDesc(SysModelAuditLog::getId).last("LIMIT 1"));
        var defaults = new ModelConfigDTO();
        if (rows.isEmpty() || rows.getFirst().getDetailJson() == null) return new StoredConfig(defaults, "");
        JsonNode detail = rows.getFirst().getDetailJson();
        // Initial SQL seed is a descriptive audit entry, rather than a full configuration snapshot.
        if (!detail.has("configuration")) return new StoredConfig(defaults, "");
        try {
            return new StoredConfig(json.treeToValue(detail.get("configuration"), ModelConfigDTO.class), detail.path("apiKeyEncrypted").asText(""));
        } catch (Exception ex) { throw new IllegalStateException("Invalid persisted model configuration", ex); }
    }

    public ModelConfigDTO getConfig() {
        var stored = storedConfig();
        ModelConfigDTO config = stored.config();
        config.setApiKeyPlain("");
        String key = decrypt(stored.encryptedKey());
        config.setApiKeyMasked(key.isEmpty() ? "" : "sk-****" + key.substring(Math.max(0, key.length() - 4)));
        return config;
    }

    /** Server-side adapter seam: contains the usable key and must never be returned by a controller. */
    public RuntimeModelConfig runtimeConfig() {
        var stored = storedConfig();
        return new RuntimeModelConfig(stored.config().getActiveProvider(), stored.config().getBaseUrl(),
            decrypt(stored.encryptedKey()), stored.config().getModelName(), stored.config().getEnableMockFallback());
    }

    public List<String> sensitiveWords() { return List.copyOf(storedConfig().config().getSensitiveWords()); }

    @Transactional(rollbackFor = Exception.class)
    public ModelConfigDTO updateConfig(ModelConfigDTO input, LoginUser user, HttpServletRequest req) {
        String encrypted = storedConfig().encryptedKey();
        if (hasText(input.getApiKeyPlain())) encrypted = encrypt(input.getApiKeyPlain().trim());
        var safe = json.valueToTree(input);
        ((ObjectNode) safe).remove(List.of("apiKeyPlain", "apiKeyMasked"));
        var detail = json.createObjectNode().set("configuration", safe);
        ((ObjectNode) detail).put("apiKeyEncrypted", encrypted);
        var item = event(user, "ADMIN", "MODEL_CONFIG", 200, req, detail, null);
        item.setModelProvider(input.getActiveProvider());
        mapper.insert(item);
        log.info("Model configuration updated by userId={} provider={}", user.id(), input.getActiveProvider());
        return getConfig();
    }

    private String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, nonce));
            return Base64.getEncoder().encodeToString(nonce) + "." + Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("Cannot encrypt model key", ex); }
    }
    private String decrypt(String value) {
        if (!hasText(value)) return "";
        try {
            var parts = value.split("\\.", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) { throw new IllegalStateException("Cannot decrypt model key; retain the configured server secret", ex); }
    }

    @Transactional(readOnly = true)
    public BigScreenStatsVO bigScreen() {
        Map<FlowEdge, Long> flowEdges = new LinkedHashMap<>();
        for (TokenFlow f : mapper.tokenFlows()) {
            String dept = "班级 · " + f.deptClass(), role = "角色 · " + f.roleType(), provider = "模型 · " + f.modelProvider();
            flowEdges.merge(new FlowEdge(dept, role), f.tokens(), Long::sum);
            flowEdges.merge(new FlowEdge(role, provider), f.tokens(), Long::sum);
        }
        Set<String> names = new LinkedHashSet<>();
        List<SankeyLink> links = new ArrayList<>();
        flowEdges.forEach((edge, value) -> { names.add(edge.source()); names.add(edge.target()); links.add(new SankeyLink(edge.source(), edge.target(), value)); });
        LocalDateTime end = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1);
        LocalDateTime start = end.minusHours(24);
        var samples = mapper.hourlySamples(start, end);
        List<HourlyTrend> trend = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            var hour = start.plusHours(i);
            var stats = samples.stream().filter(s -> !s.createdAt().isBefore(hour) && s.createdAt().isBefore(hour.plusHours(1)))
                .mapToLong(CallSample::durationMs).summaryStatistics();
            trend.add(new HourlyTrend(hour.format(DateTimeFormatter.ofPattern("HH:00")), stats.getCount(), Math.round(stats.getAverage() * 100.0) / 100.0));
        }
        return new BigScreenStatsVO(mapper.kpi(), new SankeyData(names.stream().map(SankeyNode::new).toList(), links), mapper.deptRanking(), trend);
    }

    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private record StoredConfig(ModelConfigDTO config, String encryptedKey) {}
    private record FlowEdge(String source, String target) {}
    public record RuntimeModelConfig(String activeProvider, String baseUrl, String apiKey, String modelName, boolean enableMockFallback) {
        @Override public String toString() { return "RuntimeModelConfig[provider=" + activeProvider + ", model=" + modelName + "]"; }
    }
}
