package com.flowmind.platform.workflow;

import com.fasterxml.jackson.databind.*;
import com.flowmind.platform.common.security.*;
import com.flowmind.platform.workflow.entity.WfNodeEdge;
import com.flowmind.platform.workflow.mapper.WfNodeEdgeMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowCrudAndCollabTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;
    @Autowired TokenCacheService cache;
    @Autowired PasswordEncoder encoder;
    @SpyBean WfNodeEdgeMapper elements;
    @LocalServerPort int port;
    String owner, member, outsider;
    @BeforeEach void setup() {
        reset(elements);
        for (String table : List.of("edu_submission", "wf_exec_node_log", "wf_exec_record", "wf_node_edge", "wf_workflow")) jdbc.update("DELETE FROM " + table);
        owner = jwt.generateToken(3L, "gufengming", "顾峰鸣", "STUDENT");
        member = jwt.generateToken(4L, "zhangyucheng", "张誉诚", "STUDENT");
        outsider = jwt.generateToken(5L, "haotangxin", "郝唐鑫", "STUDENT");
    }
    JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString()); }
    long create(String name) throws Exception {
        return body(mvc.perform(post("/api/v1/workflows").header("Authorization", "Bearer " + owner).contentType("application/json")
            .content(json.writeValueAsString(Map.of("wfName", name, "category", "CODE_ASSIST", "isPublic", 0))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nodes.length()").value(2)).andReturn()).path("data").path("workflow").path("id").asLong();
    }
    String canvas(boolean snapshot, String version) throws Exception {
        List<Object> nodes = new ArrayList<>();
        for (String type : List.of("INPUT", "KNOWLEDGE", "LLM", "OUTPUT")) nodes.add(Map.of("elementKey", type, "nodeType", type,
            "labelName", type, "positionJson", Map.of("x", 100, "y", 200), "configJson", Map.of("prompt", "hello")));
        List<Object> edges = List.of(Map.of("elementKey", "e1", "sourceKey", "INPUT", "targetKey", "KNOWLEDGE"),
            Map.of("elementKey", "e2", "sourceKey", "KNOWLEDGE", "targetKey", "LLM"), Map.of("elementKey", "e3", "sourceKey", "LLM", "targetKey", "OUTPUT"));
        return json.writeValueAsString(Map.of("wfName", "Java 协同工作流", "description", "事务测试", "isPublic", 0,
            "createSnapshot", snapshot, "newVersionTag", version, "nodes", nodes, "edges", edges));
    }
    @Test void createSaveQueryRollbackCloneAndDelete() throws Exception {
        long id = create("Java 初始工作流");
        mvc.perform(put("/api/v1/workflows/{id}/canvas", id).header("Authorization", "Bearer " + owner).contentType("application/json").content(canvas(true,"v1.1.0")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.workflow.nodeCount").value(4)).andExpect(jsonPath("$.data.edges.length()").value(3));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_node_edge WHERE workflow_id = ?", Integer.class, id)).isEqualTo(7);
        mvc.perform(get("/api/v1/workflows").header("Authorization", "Bearer " + owner)
            .param("keyword", "Java").param("category", "CODE_ASSIST").param("minNodeCount", "3").param("maxNodeCount", "10").param("isPublic", "0").param("scope", "MINE"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/workflows/{id}/versions", id).header("Authorization", "Bearer " + owner))
            .andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(post("/api/v1/workflows/{id}/versions/rollback", id).header("Authorization", "Bearer " + owner)
            .contentType("application/json").content("{\"versionTag\":\"v1.0.0\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.workflow.nodeCount").value(2)).andExpect(jsonPath("$.data.workflow.versionTag").value("v1.0.0"));
        var cloned = body(mvc.perform(post("/api/v1/workflows/{id}/clone", id).header("Authorization", "Bearer " + owner)).andExpect(status().isOk()).andReturn());
        assertThat(cloned.path("data").path("workflow").path("id").asLong()).isNotEqualTo(id);
        mvc.perform(delete("/api/v1/workflows/{id}", id).header("Authorization", "Bearer " + owner)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_node_edge WHERE workflow_id = ?", Integer.class, id)).isZero();
    }
    @Test void permissionsAndCombinedScope() throws Exception {
        long id = create("私有工作流");
        mvc.perform(get("/api/v1/workflows/{id}", id).header("Authorization", "Bearer " + member)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/workflows/{id}/collaborators", id).header("Authorization", "Bearer " + owner)
            .contentType("application/json").content("{\"collaboratorIds\":[4]}" )).andExpect(status().isOk());
        mvc.perform(get("/api/v1/workflows").header("Authorization", "Bearer " + member).param("scope", "COLLAB"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(put("/api/v1/workflows/{id}/canvas", id).header("Authorization", "Bearer " + member).contentType("application/json").content(canvas(false,""))).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/workflows/{id}", id).header("Authorization", "Bearer " + member)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/workflows").header("Authorization", "Bearer " + outsider)).andExpect(jsonPath("$.data.total").value(0));
    }
    @Test void failedChildInsertRollsBackWholeCanvas() throws Exception {
        long id = create("事务原始版本");
        doThrow(new IllegalStateException("injected persistence failure")).when(elements).insert(any(WfNodeEdge.class));
        mvc.perform(put("/api/v1/workflows/{id}/canvas", id).header("Authorization", "Bearer " + owner).contentType("application/json").content(canvas(true,"v2")))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(50000));
        reset(elements);
        assertThat(jdbc.queryForObject("SELECT node_count FROM wf_workflow WHERE id = ?", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_node_edge WHERE workflow_id = ?", Integer.class, id)).isEqualTo(2);
    }
    @Test void cloneRetainsGraphAndInvalidCanvasDoesNotMutateData() throws Exception {
        long id = create("校验与克隆");
        mvc.perform(put("/api/v1/workflows/{id}/canvas", id).header("Authorization", "Bearer " + owner).contentType("application/json").content(canvas(true,"v2")))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/workflows/{id}/clone", id).header("Authorization", "Bearer " + owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nodes.length()").value(4)).andExpect(jsonPath("$.data.edges.length()").value(3))
            .andExpect(jsonPath("$.data.workflow.creatorId").value(3)).andExpect(jsonPath("$.data.workflow.isPublic").value(0));
        String invalid = canvas(false, "").replace("\"targetKey\":\"KNOWLEDGE\"", "\"targetKey\":\"missing\"");
        mvc.perform(put("/api/v1/workflows/{id}/canvas", id).header("Authorization", "Bearer " + owner).contentType("application/json").content(invalid))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        mvc.perform(put("/api/v1/workflows/{id}/canvas", id).header("Authorization", "Bearer " + owner).contentType("application/json").content(canvas(true,"v2")))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/workflows/{id}", id).header("Authorization", "Bearer " + owner))
            .andExpect(jsonPath("$.data.nodes.length()").value(4)).andExpect(jsonPath("$.data.edges.length()").value(3)).andExpect(jsonPath("$.data.workflow.versionTag").value("v2"));
    }
    @Test void invalidPayloadsErrorsCorsAndRevokedTokens() throws Exception {
        mvc.perform(get("/api/v1/workflows")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40100));
        mvc.perform(get("/api/v1/workflows/999999").header("Authorization", "Bearer " + owner)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(40004));
        mvc.perform(post("/api/v1/workflows").header("Authorization", "Bearer " + owner).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        mvc.perform(get("/api/v1/workflows").header("Authorization", "Bearer " + owner).param("minNodeCount", "8").param("maxNodeCount", "2"))
            .andExpect(status().isBadRequest());
        mvc.perform(options("/api/v1/workflows").header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "PUT"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        cache.blacklistToken(jwt.parseToken(owner).getId(), 60000);
        mvc.perform(get("/api/v1/workflows").header("Authorization", "Bearer " + owner)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40102));
    }
    @Test void dashboardUsesDatabaseCountsAndQuota() throws Exception {
        create("统计工作流");
        mvc.perform(get("/api/v1/workflows/dashboard").header("Authorization", "Bearer " + owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.myWorkflowCount").value(1))
            .andExpect(jsonPath("$.data.remainingTokenQuota").value(46800));
    }
    @Test void seededAccountsUseWorkingBcryptPasswords() {
        var hashes = jdbc.queryForList("SELECT password_hash FROM sys_user", String.class);
        assertThat(hashes).hasSize(6);
        for (String hash : hashes) assertThat(encoder.matches("123456", hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT real_name FROM sys_user WHERE username = 'admin'", String.class)).isEqualTo("顾峰鸣(管理员)");
        assertThat(jdbc.queryForObject("SELECT real_name FROM sys_user WHERE username = 'teacher_litian'", String.class)).isEqualTo("李甜");
    }
    static class Inbox extends TextWebSocketHandler {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) { messages.add(message.getPayload()); }
        String await(String type) throws Exception {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < until) {
                String value = messages.poll(200, TimeUnit.MILLISECONDS);
                if (value != null && value.contains("\"type\":\"" + type + "\"")) return value;
            }
            throw new AssertionError("Missing socket event " + type);
        }
    }
    @Test void realWebSocketsAuthenticateBroadcastAndIsolateRooms() throws Exception {
        long id = create("实时协作"); long other = create("独立房间");
        mvc.perform(put("/api/v1/workflows/{id}/collaborators", id).header("Authorization", "Bearer " + owner)
            .contentType("application/json").content("{\"collaboratorIds\":[4]}" )).andExpect(status().isOk());
        var client = new StandardWebSocketClient(); var a = new Inbox(); var b = new Inbox(); var c = new Inbox();
        String base = "ws://localhost:" + port + "/ws/collab/";
        WebSocketSession sa = client.execute(a, base + id + "?token=" + owner).get(5, TimeUnit.SECONDS);
        WebSocketSession sb = client.execute(b, base + id + "?token=" + member).get(5, TimeUnit.SECONDS);
        WebSocketSession sc = client.execute(c, base + other + "?token=" + owner).get(5, TimeUnit.SECONDS);
        try {
            assertThat(b.await("PRESENCE_SYNC")).contains("顾峰鸣", "张誉诚"); c.await("PRESENCE_SYNC"); c.messages.clear(); a.messages.clear(); b.messages.clear();
            sa.sendMessage(new TextMessage("{\"type\":\"CURSOR_MOVE\",\"senderId\":999,\"workflowId\":999,\"payload\":{\"x\":500,\"y\":300}}"));
            JsonNode cursor = json.readTree(b.await("CURSOR_MOVE"));
            assertThat(cursor.path("senderId").asLong()).isEqualTo(3); assertThat(cursor.path("workflowId").asLong()).isEqualTo(id);
            assertThat(cursor.path("senderColor").asText()).isEqualTo("#EC4899");
            assertThat(a.messages.stream().noneMatch(v -> v.contains("CURSOR_MOVE"))).isTrue();
            assertThat(c.messages.poll(150, TimeUnit.MILLISECONDS)).isNull();
            sa.sendMessage(new TextMessage("{\"type\":\"GRAPH_DELTA\",\"payload\":{\"nodeKey\":\"INPUT\",\"position\":{\"x\":500,\"y\":300}}}"));
            assertThat(b.await("GRAPH_DELTA")).contains("500");
            assertThatThrownBy(() -> client.execute(new Inbox(), base + id + "?token=" + outsider).get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            cache.blacklistToken(jwt.parseToken(member).getId(), 60000);
            sb.sendMessage(new TextMessage("{\"type\":\"CURSOR_MOVE\",\"payload\":{\"x\":1,\"y\":2}}"));
            a.await("PRESENCE_SYNC");
        } finally { sa.close(); sb.close(); sc.close(); }
    }
}
