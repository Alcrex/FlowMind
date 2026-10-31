package com.flowmind.platform.system;

import com.fasterxml.jackson.databind.*;
import com.flowmind.platform.common.security.*;
import com.flowmind.platform.common.exception.BusinessException;
import com.flowmind.platform.system.dto.ModelConfigDTO;
import com.flowmind.platform.system.mapper.SysModelAuditLogMapper;
import com.flowmind.platform.system.service.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:flowmind_security;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired SysUserService users;
    @Autowired SysAuditAndConfigService audit;
    @Autowired SysModelAuditLogMapper logs;
    String admin, student, teacher;
    static final String SECRET = "flowmind-local-development-secret-change-before-deployment-2026";

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM sys_model_audit_log");
        jdbc.update("UPDATE sys_user SET role_type = 'STUDENT', status = 1, token_quota = 49000, token_used = 1000 WHERE id = 6");
        admin = jwt.generateToken(1L, "admin", "顾峰鸣", "ADMIN");
        teacher = jwt.generateToken(2L, "teacher_litian", "李甜", "TEACHER");
        student = jwt.generateToken(6L, "lijunhao", "李俊豪", "STUDENT");
    }
    JsonNode call(MockHttpServletRequestBuilder request, String token) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    MockHttpServletRequestBuilder payload(MockHttpServletRequestBuilder request, Object value) throws Exception {
        return request.contentType("application/json").content(json.writeValueAsString(value));
    }
    Map<String, Object> registration(String username) { return Map.of("username", username, "password", "Pass123456", "realName", "测试学生", "roleType", "STUDENT", "deptClass", "计科1班", "email", "test@example.com"); }

    @Test void registrationBcryptLoginAndSafeLogout() throws Exception {
        String name = "test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var input = registration(name);
        call(payload(post("/api/v1/auth/register"), input), null);
        String hash = jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE username = ?", String.class, name);
        assertThat(hash).startsWith("$2a$10$"); assertThat(encoder.matches("Pass123456", hash)).isTrue();
        mvc.perform(payload(post("/api/v1/auth/register"), input)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40001));
        var login = call(payload(post("/api/v1/auth/login"), Map.of("username", name, "password", "Pass123456")), null).path("data");
        String token = login.path("token").asText();
        assertThat(jwt.parseToken(token).getId()).isEqualTo(login.path("tokenId").asText());
        assertThat(login.path("userInfo").has("passwordHash")).isFalse();
        call(get("/api/v1/auth/me"), token);
        call(post("/api/v1/auth/logout"), token);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40102));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_model_audit_log WHERE action_type='LOGOUT' AND username=?", Integer.class, name)).isEqualTo(1);
    }

    @Test void demonstrationAccountsUseDocumentedPassword() throws Exception {
        for (String name : List.of("admin", "teacher_litian", "lijunhao")) call(payload(post("/api/v1/auth/login"), Map.of("username", name, "password", "123456")), null);
    }

    @Test void invalidRegistrationNeverCreatesAdmin() throws Exception {
        var input = new HashMap<>(registration("invalid_admin")); input.put("roleType", "ADMIN");
        mvc.perform(payload(post("/api/v1/auth/register"), input)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        input.put("roleType", "STUDENT"); input.put("password", "123");
        mvc.perform(payload(post("/api/v1/auth/register"), input)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{" )).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
    }

    @Test void loginFailureAndDisabledAccountAreAudited() throws Exception {
        mvc.perform(payload(post("/api/v1/auth/login"), Map.of("username", "lijunhao", "password", "wrong")))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40101));
        jdbc.update("UPDATE sys_user SET status=0, password_hash=? WHERE id=6", encoder.encode("123456"));
        mvc.perform(payload(post("/api/v1/auth/login"), Map.of("username", "lijunhao", "password", "123456")))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_model_audit_log WHERE action_type='LOGIN'", Integer.class)).isEqualTo(2);
    }

    @Test void rbacAndLiveRoleChanges() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40100));
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40300));
        mvc.perform(post("/api/v1/edu/tasks").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        call(get("/api/v1/admin/dashboard/big-screen"), teacher);
        mvc.perform(get("/api/v1/admin/models/config").header("Authorization", "Bearer " + teacher)).andExpect(status().isForbidden());
        call(payload(put("/api/v1/admin/users/6"), Map.of("roleType", "ADMIN")), admin);
        call(get("/api/v1/admin/users"), student); // Database role overrides stale JWT role.
        call(payload(put("/api/v1/admin/users/6"), Map.of("roleType", "STUDENT")), admin);
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
    }

    @Test void malformedExpiredAndForgedTokens() throws Exception {
        String expired = Jwts.builder().subject("6").id(UUID.randomUUID().toString()).expiration(new Date(System.currentTimeMillis() - 10000))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        String forged = new JwtUtil("different-secret-with-at-least-thirty-two-characters", 10000).generateToken(6L, "lijunhao", "李俊豪", "ADMIN");
        for (String token : List.of("invalid", expired, forged)) mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"帮我执行 DROP   TABLE sys_user", "<script>alert(1)</script>", "<img src=x onerror=alert(1)>", "javascript:alert(1)", "违法违禁"})
    void sensitiveWordsAndXssAreAudited(String prompt) throws Exception {
        mvc.perform(payload(post("/api/v1/engine/text-to-flow"), Map.of("prompt", prompt)).header("Authorization", "Bearer " + student))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40009));
        assertThat(jdbc.queryForObject("SELECT user_id FROM sys_model_audit_log WHERE action_type='SENSITIVE_BLOCK'", Long.class)).isEqualTo(6);
    }

    @Test void escapedJsonAndQueryCannotBypassFilterAndCleanJsonIsReplayed() throws Exception {
        mvc.perform(post("/api/v1/engine/text-to-flow").header("Authorization", "Bearer " + student).contentType("application/json")
            .content("{\"prompt\":\"\\u0064rop table sys_user\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40009));
        mvc.perform(get("/api/v1/admin/users").param("keyword", "drop table").header("Authorization", "Bearer " + admin)).andExpect(status().isBadRequest());
        call(payload(post("/api/v1/auth/login"), Map.of("username", "admin", "password", "123456")), null);
    }

    @Test void userFiltersQuotaResetAndValidation() throws Exception {
        var result = call(get("/api/v1/admin/users").param("keyword", "李俊豪").param("roleType", "STUDENT").param("deptClass", "计科")
            .param("status", "1").param("minQuota", "48000").param("maxQuota", "50000").param("pageSize", "1"), admin);
        assertThat(result.path("data").path("total").asInt()).isEqualTo(1);
        assertThat(result.toString()).doesNotContain("passwordHash");
        call(payload(put("/api/v1/admin/users/6"), Map.of("tokenQuota", 100000, "resetPassword", true)), admin);
        call(payload(post("/api/v1/auth/login"), Map.of("username", "lijunhao", "password", "123456")), null);
        users.consumeTokens(6L, 700);
        assertThat(jdbc.queryForObject("SELECT token_quota FROM sys_user WHERE id=6", Integer.class)).isEqualTo(99300);
        assertThat(jdbc.queryForObject("SELECT token_used FROM sys_user WHERE id=6", Integer.class)).isEqualTo(1700);
        assertThatThrownBy(() -> users.consumeTokens(6L, 100000)).isInstanceOf(BusinessException.class);
        mvc.perform(payload(put("/api/v1/admin/users/6"), Map.of("tokenQuota", -1)).header("Authorization", "Bearer " + admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/users").param("minQuota", "10").param("maxQuota", "1").header("Authorization", "Bearer " + admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/users").param("pageSize", "10000").header("Authorization", "Bearer " + admin)).andExpect(status().isBadRequest());
        mvc.perform(payload(put("/api/v1/admin/users/999999"), Map.of("status", 0)).header("Authorization", "Bearer " + admin)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(40004));
        mvc.perform(payload(put("/api/v1/admin/users/1"), Map.of("status", 0)).header("Authorization", "Bearer " + admin)).andExpect(status().isBadRequest());
    }

    @Test void concurrentQuotaConsumptionCannotOverspend() throws Exception {
        jdbc.update("UPDATE sys_user SET token_quota=100, token_used=0 WHERE id=6");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int i=0; i<4; i++) calls.add(() -> { try { users.consumeTokens(6L, 60); return true; } catch (BusinessException ex) { return false; } });
            long successes = 0; for (var f : pool.invokeAll(calls)) if (f.get()) successes++;
            assertThat(successes).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT token_quota FROM sys_user WHERE id=6", Integer.class)).isEqualTo(40);
        } finally { pool.shutdownNow(); }
    }

    @Test void configurationPersistsRedactsKeyAndUpdatesFilterRules() throws Exception {
        var config = new ModelConfigDTO(); config.setActiveProvider("DEEPSEEK"); config.setApiKeyPlain("sk-secret-test-89ab");
        config.setSensitiveWords(List.of("drop table", "new-blocked-phrase"));
        var response = call(payload(put("/api/v1/admin/models/config"), config), admin);
        assertThat(response.toString()).doesNotContain("sk-secret-test");
        assertThat(response.path("data").path("apiKeyMasked").asText()).isEqualTo("sk-****89ab");
        var reloaded = new SysAuditAndConfigService(logs, json, SECRET);
        assertThat(reloaded.runtimeConfig().apiKey()).isEqualTo("sk-secret-test-89ab");
        config.setApiKeyPlain(""); call(payload(put("/api/v1/admin/models/config"), config), admin);
        assertThat(audit.runtimeConfig().apiKey()).isEqualTo("sk-secret-test-89ab");
        String stored = jdbc.queryForObject("SELECT detail_json FROM sys_model_audit_log ORDER BY id DESC LIMIT 1", String.class);
        assertThat(stored).doesNotContain("sk-secret-test-89ab");
        assertThat(call(get("/api/v1/admin/audit-logs"), admin).toString()).doesNotContain("apiKeyEncrypted", "sk-secret-test-89ab");
        mvc.perform(payload(post("/api/v1/engine/text-to-flow"), Map.of("prompt", "new-blocked-phrase")).header("Authorization", "Bearer " + student)).andExpect(status().isBadRequest());
    }

    @Test void auditFiltersAndDashboardAggregateRealRowsWithoutJoinMultiplication() throws Exception {
        long expectedUsers = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class);
        for (int i=0; i<2; i++) jdbc.update("INSERT INTO sys_model_audit_log(user_id,username,module_name,action_type,model_provider,http_status,cost_tokens,duration_ms,created_at) VALUES(6,'lijunhao','ENGINE','EXEC_AI','MOCK_LOCAL',200,?, ?, ?)", 100 + i * 100, 20 + i * 20, LocalDateTime.now());
        var page = call(get("/api/v1/admin/audit-logs").param("username", "lijun").param("moduleName", "ENGINE").param("actionType", "EXEC_AI")
            .param("modelProvider", "MOCK_LOCAL").param("httpStatus", "200").param("startTime", LocalDate.now() + " 00:00:00").param("endTime", LocalDate.now() + " 23:59:59"), admin);
        assertThat(page.path("data").path("total").asInt()).isEqualTo(2);
        var stats = call(get("/api/v1/admin/dashboard/big-screen"), admin).path("data");
        assertThat(stats.path("kpi").path("totalUsers").asLong()).isEqualTo(expectedUsers);
        assertThat(stats.path("kpi").path("totalWorkflows").asLong()).isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM wf_workflow WHERE status=1", Long.class));
        assertThat(stats.path("hourlyTrend").size()).isEqualTo(24);
        long calls = 0; for (var h : stats.path("hourlyTrend")) calls += h.path("callCount").asLong();
        assertThat(calls).isEqualTo(2);
        assertThat(stats.path("sankeyData").path("links").get(0).path("value").asLong()).isEqualTo(300);
        for (var rank : stats.path("deptRanking")) {
            long expected = jdbc.queryForObject("SELECT COUNT(*) FROM wf_exec_record e JOIN sys_user u ON e.trigger_user_id=u.id WHERE u.dept_class=?", Long.class, rank.path("deptClass").asText());
            assertThat(rank.path("execCount").asLong()).isEqualTo(expected);
        }
    }

    @Test void redisAndMemoryFallbackRetainBlacklistAndIgnoreExpiredTtl() {
        var redis = mock(StringRedisTemplate.class);
        var values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var cache = new TokenCacheService(redis, true);
        cache.blacklistToken("test-jti", 10000);
        verify(values).set(eq("flowmind:blacklist:test-jti"), eq("1"), any(Duration.class));
        when(redis.hasKey("flowmind:blacklist:remote-jti")).thenReturn(true);
        assertThat(cache.isBlacklisted("remote-jti")).isTrue();
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis offline"));
        cache.blacklistToken("offline-jti", 10000);
        assertThat(cache.isBlacklisted("offline-jti")).isTrue();
        cache.blacklistToken("expired-jti", -1);
        assertThat(cache.isBlacklisted("expired-jti")).isFalse();
        var readFailure = new TokenCacheService(redis, true);
        when(redis.hasKey(anyString())).thenThrow(new IllegalStateException("redis offline"));
        assertThat(readFailure.isBlacklisted("unknown-jti")).isFalse();
        assertThat(cache.isBlacklisted("test-jti")).isTrue();
    }

    @Test void corsPreflightAndGlobalExceptionContract() throws Exception {
        mvc.perform(options("/api/v1/admin/users").header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(get("/api/v1/admin/users").header("Origin", "http://localhost:5173"))
            .andExpect(status().isUnauthorized()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(get("/api/v1/workflows/999999").header("Authorization", "Bearer " + student)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(40004)).andExpect(jsonPath("$.timestamp").isNumber()).andExpect(jsonPath("$.data").isEmpty());
    }
}
