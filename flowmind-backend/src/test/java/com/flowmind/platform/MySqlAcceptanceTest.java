package com.flowmind.platform;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in acceptance against an empty disposable MySQL schema; never initializes flowmind_db. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql-test")
@EnabledIfEnvironmentVariable(named = "FLOWMIND_MYSQL_TEST_URL", matches = ".+")
class MySqlAcceptanceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll static void initialize(@Autowired JdbcTemplate jdbc) throws Exception {
        try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MySQL");
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isGreaterThanOrEqualTo(8);
            assertThat(connection.getCatalog()).matches("flowmind_acceptance_[a-f0-9]{32}");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()", Long.class)).isZero();
            String sql = Files.readString(Path.of("../sql/init_flowmind.sql"), StandardCharsets.UTF_8)
                .replaceFirst("CREATE DATABASE[^;]+;", "").replace("USE `flowmind_db`;", "");
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        }
    }

    JsonNode call(MockHttpServletRequestBuilder request, String token, Object payload) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (payload != null) request.contentType("application/json").content(json.writeValueAsBytes(payload));
        var response = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
    String login(String username) throws Exception {
        return call(post("/api/v1/auth/login"), null, Map.of("username", username, "password", "123456")).path("token").asText();
    }

    @Test void schemaAndForeignKeysMatchProductionDesign() {
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()", String.class))
            .containsExactlyInAnyOrder("sys_user", "sys_model_audit_log", "wf_workflow", "wf_node_edge", "wf_exec_record", "wf_exec_node_log", "edu_task", "edu_submission");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND delete_rule='CASCADE'", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND data_type='json'", Long.class)).isEqualTo(11);
    }

    @Test void authenticationWorkflowEngineEducationAndAnalyticsUseRealMySql() throws Exception {
        String student = login("gufengming"), teacher = login("teacher_litian"), admin = login("admin");
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + student)).andExpect(status().isForbidden());
        var flow = call(post("/api/v1/engine/text-to-flow"), student, Map.of("prompt", "清洗文本并检索知识库回答问题", "category", "CODE_ASSIST"));
        long workflowId = flow.path("workflow").path("id").asLong();
        call(put("/api/v1/workflows/" + workflowId + "/collaborators"), student, Map.of("collaboratorIds", List.of(4)));
        String member = login("zhangyucheng");
        assertThat(call(get("/api/v1/workflows").param("scope", "COLLAB").param("keyword", "清洗文本"), member, null).path("total").asLong()).isEqualTo(1);
        var saved = call(put("/api/v1/workflows/" + workflowId + "/canvas"), student, Map.of(
            "wfName", flow.path("workflow").path("wfName").asText(), "nodes", flow.path("nodes"), "edges", flow.path("edges"), "isPublic", 1, "createSnapshot", true, "newVersionTag", "v-mysql"));
        int nodeCount = saved.path("nodes").size();
        assertThat(nodeCount).isEqualTo(5);
        var request = Map.of("workflowId", workflowId, "triggerMode", "SANDBOX", "inputPayload", Map.of("text", "Java SQL 参数查询"));
        var execution = call(post("/api/v1/engine/execute/sync"), student, request);
        assertThat(execution.path("record").path("execStatus").asText()).isEqualTo("SUCCESS");
        assertThat(execution.path("nodeLogs").size()).isEqualTo(nodeCount);
        assertThat(execution.path("record").path("totalTokens").asInt()).isPositive();
        var stream = mvc.perform(post("/api/v1/engine/execute/stream").header("Authorization", "Bearer " + student)
            .contentType("application/json").content(json.writeValueAsBytes(request))).andExpect(request().asyncStarted()).andReturn();
        var streamed = mvc.perform(asyncDispatch(stream)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(streamed).contains("event:NODE_TOKEN", "event:WORKFLOW_COMPLETE");
        long forkId = call(post("/api/v1/market/templates/" + workflowId + "/fork"), member, null).path("newWorkflowId").asLong();
        assertThat(call(get("/api/v1/workflows/" + forkId), member, null).path("nodes").size()).isEqualTo(nodeCount);
        var task = call(post("/api/v1/edu/tasks"), teacher, Map.of(
            "taskTitle", "MySQL全链路验收", "courseName", "Web实训", "targetClass", "计科1班", "taskRequirement", "设计并验证工作流",
            "requiredNodesJson", Map.of("minNodes", 3, "mustInclude", List.of("INPUT", "LLM", "OUTPUT")),
            "deadline", LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), "taskStatus", 1));
        long taskId = task.path("id").asLong();
        var submission = call(post("/api/v1/edu/submissions"), student, Map.of("taskId", taskId, "workflowId", workflowId, "studentReport", "MySQL 实训设计与验证记录"));
        long submissionId = submission.path("id").asLong();
        assertThat(submission.path("autoCheckPassed").asInt()).isEqualTo(1);
        call(post("/api/v1/workflows/" + workflowId + "/versions/rollback"), student, Map.of("versionTag", "v1.0.0"));
        assertThat(call(get("/api/v1/workflows/" + workflowId), student, null).path("nodes").size()).isEqualTo(2);
        var snapshot = call(get("/api/v1/edu/submissions/" + submissionId), teacher, null).path("canvasSnapshotJson");
        assertThat(snapshot.path("nodes").size()).isEqualTo(nodeCount);
        var replay = call(post("/api/v1/engine/execute/sync"), teacher, Map.of("workflowId", workflowId, "triggerMode", "TEACHER_REVIEW",
            "inputPayload", Map.of("text", "Java SQL 参数查询"), "canvasOverride", Map.of("nodes", snapshot.path("nodes"), "edges", snapshot.path("edges"))));
        assertThat(replay.path("record").path("execStatus").asText()).isEqualTo("SUCCESS");
        call(put("/api/v1/edu/submissions/" + submissionId + "/grade"), teacher, Map.of("reviewStatus", 1, "score", 95, "teacherComment", "设计合理，验证通过"));
        var analytics = call(get("/api/v1/edu/analytics").param("taskId", Long.toString(taskId)), teacher, null);
        assertThat(analytics.path("summary").path("gradedCount").asLong()).isEqualTo(1);
        assertThat(analytics.path("radarDimensions").size()).isEqualTo(5);
        assertThat(call(get("/api/v1/admin/dashboard/big-screen"), admin, null).path("kpi").path("totalExecutions").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(call(get("/api/v1/admin/audit-logs").param("actionType", "EXEC_AI"), admin, null).path("total").asLong()).isPositive();
        call(delete("/api/v1/edu/tasks/" + taskId), teacher, null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM edu_submission WHERE task_id=?", Long.class, taskId)).isZero();
        call(delete("/api/v1/workflows/" + workflowId), student, null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_node_edge WHERE workflow_id=?", Long.class, workflowId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_exec_record WHERE workflow_id=?", Long.class, workflowId)).isZero();
        call(delete("/api/v1/workflows/" + forkId), member, null);
        call(post("/api/v1/auth/logout"), student, null);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + student)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40102));
    }
}
