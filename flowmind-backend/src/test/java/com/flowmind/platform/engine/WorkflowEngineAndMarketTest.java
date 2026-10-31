package com.flowmind.platform.engine;

import com.fasterxml.jackson.databind.*;
import com.flowmind.platform.common.api.ErrorCode;
import com.flowmind.platform.common.exception.BusinessException;
import com.flowmind.platform.common.security.*;
import com.flowmind.platform.engine.dto.*;
import com.flowmind.platform.engine.mapper.WfExecNodeLogMapper;
import com.flowmind.platform.engine.runtime.*;
import com.flowmind.platform.engine.service.*;
import com.flowmind.platform.workflow.entity.*;
import com.flowmind.platform.workflow.mapper.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:flowmind_engine_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkflowEngineAndMarketTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    @Autowired JwtUtil jwt;
    @Autowired DagTopologicalSorter sorter;
    @Autowired WorkflowDagExecutor executor;
    @Autowired WfEngineService service;
    @Autowired TemplateMarketService market;
    @Autowired WfWorkflowMapper workflows;
    @SpyBean WfNodeEdgeMapper elements;
    @SpyBean WfExecNodeLogMapper logs;
    String token, outsider, admin;
    final LoginUser user = new LoginUser(4L,"zhangyucheng","张誉诚","STUDENT","");
    @BeforeEach void setup() {
        reset(elements, logs);
        for (String table : List.of("edu_submission","wf_exec_node_log","wf_exec_record","wf_node_edge","wf_workflow")) jdbc.update("DELETE FROM " + table);
        jdbc.update("UPDATE sys_user SET token_quota = 50000, token_used = 0, status = 1");
        jdbc.update("DELETE FROM sys_model_audit_log WHERE module_name = 'ENGINE'");
        jdbc.update("DELETE FROM sys_model_audit_log WHERE action_type = 'MODEL_CONFIG'");
        token = jwt.generateToken(4L,"zhangyucheng","张誉诚","STUDENT");
        outsider = jwt.generateToken(5L,"haotangxin","郝唐鑫","STUDENT");
        admin = jwt.generateToken(1L,"admin","顾峰鸣","ADMIN");
    }
    JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)); }
    JsonNode generate(String prompt) throws Exception {
        return body(mvc.perform(post("/api/v1/engine/text-to-flow").header("Authorization","Bearer " + token).contentType("application/json")
            .content(json.writeValueAsString(Map.of("prompt",prompt,"category","CODE_ASSIST"))))
            .andExpect(status().isOk()).andReturn()).path("data");
    }
    Map<String,Object> payload(long id,String text) { return Map.of("workflowId",id,"triggerMode","SANDBOX","inputPayload",Map.of("text",text)); }
    JsonNode execute(long id,String text) throws Exception {
        return body(mvc.perform(post("/api/v1/engine/execute/sync").header("Authorization","Bearer " + token).contentType("application/json")
            .content(json.writeValueAsString(payload(id,text)))).andExpect(status().isOk()).andReturn()).path("data");
    }
    long generatedId(String prompt) throws Exception { return generate(prompt).path("workflow").path("id").asLong(); }
    WfNodeEdge node(String key,String type) { var n = new WfNodeEdge(); n.setElementKey(key); n.setNodeType(type); return n; }
    WfNodeEdge edge(String key,String source,String target) { var n = new WfNodeEdge(); n.setElementKey(key); n.setSourceKey(source); n.setTargetKey(target); n.setSourceHandle("default"); return n; }
    long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table,Long.class); }
    void configure(long id,String key,Map<String,?> config) throws Exception { jdbc.update("UPDATE wf_node_edge SET config_json = ? WHERE workflow_id = ? AND element_key = ?",json.writeValueAsString(config),id,key); }

    @Test void kahnOrdersDependenciesAndRejectsCycle() {
        var nodes = List.of(node("out","OUTPUT"),node("in","INPUT"),node("llm","LLM"));
        var edges = new ArrayList<>(List.of(edge("e1","in","llm"),edge("e2","llm","out")));
        assertThat(sorter.sortAndValidate(nodes,edges)).extracting(WfNodeEdge::getElementKey).containsExactly("in","llm","out");
        edges.add(edge("e3","out","llm"));
        assertThatThrownBy(() -> sorter.sortAndValidate(nodes,edges)).isInstanceOfSatisfying(BusinessException.class,e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DAG_CYCLE_ERROR));
    }
    @Test void topologyRejectsMissingEndpointsDanglingDuplicateAndIsolatedNodes() {
        assertThatThrownBy(() -> sorter.sortAndValidate(List.of(node("in","INPUT")),List.of())).isInstanceOf(BusinessException.class);
        var nodes = List.of(node("in","INPUT"),node("out","OUTPUT"));
        assertThatThrownBy(() -> sorter.sortAndValidate(nodes,List.of(edge("e","in","missing")))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sorter.sortAndValidate(List.of(node("in","INPUT"),node("in","OUTPUT")),List.of())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sorter.sortAndValidate(nodes,List.of())).isInstanceOf(BusinessException.class);
    }
    @Test void interpolationHandlesNestedValuesMissingNullAndReplacementCharacters() {
        Map<String,Object> context = new HashMap<>(); context.put("in",Map.of("text","$1\\test","obj",Map.of("n",3))); context.put("empty",null);
        assertThat(executor.interpolate("{{ in.text }} / {{in.obj.n}} / {{missing.output}} / {{empty}}",context)).isEqualTo("$1\\test / 3 /  / ");
    }
    @Test void allSixNodeTypesPersistPrunedLogsAndAccurateQuota() throws Exception {
        var canvas = generate("清洗文本，如果是Java问题则检索知识库并调用模型，否则给出通用回答");
        long id = canvas.path("workflow").path("id").asLong();
        JsonNode detail = execute(id,"  Java SQL 拼接存在漏洞，需要测试  ");
        assertThat(detail.path("record").path("execStatus").asText()).isEqualTo("SUCCESS");
        Map<String,String> statuses = new HashMap<>(); Set<String> types = new HashSet<>(); int sum = 0;
        for (var row : detail.path("nodeLogs")) { statuses.put(row.path("nodeKey").asText(),row.path("stepStatus").asText()); types.add(row.path("nodeType").asText()); sum += row.path("tokenCost").asInt(); }
        assertThat(types).containsExactlyInAnyOrder("INPUT","CODE","CONDITION","KNOWLEDGE","LLM","OUTPUT");
        assertThat(statuses).containsEntry("node_llm_1","SUCCESS").containsEntry("node_llm_fallback","SKIPPED").containsEntry("node_output_1","SUCCESS");
        assertThat(detail.path("record").path("finalOutput").path("report").asText()).contains("PreparedStatement");
        assertThat(detail.path("record").path("totalTokens").asInt()).isEqualTo(sum).isPositive();
        assertThat(jdbc.queryForObject("SELECT token_quota FROM sys_user WHERE id=4",Integer.class)).isEqualTo(50000-sum);
        assertThat(jdbc.queryForObject("SELECT token_used FROM sys_user WHERE id=4",Integer.class)).isEqualTo(sum);
        assertThat(count("wf_exec_record")).isEqualTo(1); assertThat(count("wf_exec_node_log")).isEqualTo(canvas.path("nodes").size());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_model_audit_log WHERE module_name='ENGINE'",Long.class)).isEqualTo(1);
    }
    @Test void falseBranchPrunesKnowledgeAndKeepsSharedOutputAlive() throws Exception {
        long id = generatedId("如果是Java则检索规约后解答，否则回答一般问题");
        var detail = execute(id,"如何编写周报");
        assertThat(detail.path("record").path("execStatus").asText()).isEqualTo("SUCCESS");
        Map<String,String> status = new HashMap<>(); detail.path("nodeLogs").forEach(n -> status.put(n.path("nodeKey").asText(),n.path("stepStatus").asText()));
        assertThat(status).containsEntry("node_know_1","SKIPPED").containsEntry("node_llm_1","SKIPPED").containsEntry("node_llm_fallback","SUCCESS").containsEntry("node_output_1","SUCCESS");
    }
    @Test void quotaZeroReturns402WithoutCreatingRecords() throws Exception {
        long id = generatedId("生成代码测试建议"); jdbc.update("UPDATE sys_user SET token_quota=0 WHERE id=4");
        mvc.perform(post("/api/v1/engine/execute/sync").header("Authorization","Bearer " + token).contentType("application/json").content(json.writeValueAsString(payload(id,"test"))))
            .andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.code").value(40201));
        assertThat(count("wf_exec_record")).isZero(); assertThat(count("wf_exec_node_log")).isZero();
    }
    @Test void insufficientMidRunPersistsFailureAndNeverOverdraws() throws Exception {
        long id = generatedId("检索规约并回答代码问题"); jdbc.update("UPDATE sys_user SET token_quota=70 WHERE id=4");
        var detail = execute(id,"SQL"); assertThat(detail.path("record").path("execStatus").asText()).isEqualTo("FAILED");
        assertThat(detail.path("record").path("totalTokens").asInt()).isEqualTo(64);
        assertThat(jdbc.queryForObject("SELECT token_quota FROM sys_user WHERE id=4",Integer.class)).isEqualTo(6);
        assertThat(detail.path("nodeLogs").findValuesAsText("stepStatus")).contains("ERROR","SKIPPED");
    }
    @Test void invalidCanvasReturnsDagErrorBeforeBilling() throws Exception {
        long id = generatedId("回答问题");
        jdbc.update("UPDATE wf_node_edge SET target_key='node_input_1' WHERE workflow_id=? AND element_type='EDGE' AND source_key='node_llm_1'",id);
        mvc.perform(post("/api/v1/engine/execute/sync").header("Authorization","Bearer " + token).contentType("application/json").content(json.writeValueAsString(payload(id,"hello"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40005));
        assertThat(count("wf_exec_record")).isZero();
    }
    @Test void missingRequiredInputAndUnsupportedRuleProduceDurableNodeErrors() throws Exception {
        long id = generatedId("清洗文本然后回答");
        var missing = execute(id,""); assertThat(missing.path("nodeLogs").get(0).path("stepStatus").asText()).isEqualTo("ERROR");
        configure(id,"node_code_1",Map.of("ruleType","EXEC_SHELL","expression","echo unsafe"));
        var invalid = execute(id,"hello"); assertThat(invalid.path("record").path("execStatus").asText()).isEqualTo("FAILED");
        assertThat(invalid.path("record").path("totalTokens").asInt()).isZero();
    }
    @Test void safeCodeOperationsAndJsonOutput() throws Exception {
        long id = generatedId("清洗文本并回答");
        for (var cfg : List.of(Map.of("ruleType","REGEX_EXTRACT","expression","[0-9]+","inputTemplate","{{input.text}}"),
                               Map.of("ruleType","LENGTH","expression","{{input.text}}"),Map.of("ruleType","JSON_FORMAT","expression","{{input.text}}"))) {
            configure(id,"node_code_1",cfg); configure(id,"node_output_1",Map.of("outputFormat","JSON","resultTemplate","{\"value\":\"{{node_code_1.output}}\"}"));
            var detail = execute(id,"abc123");
            if (cfg.get("ruleType").equals("JSON_FORMAT")) assertThat(detail.path("record").path("execStatus").asText()).isEqualTo("FAILED");
            else assertThat(detail.path("record").path("finalOutput").path("node_output_1").path("value").asText()).isIn("123","6");
        }
        configure(id,"node_code_1",Map.of("ruleType","JSON_FORMAT","expression","{{input.text}}"));
        configure(id,"node_output_1",Map.of("outputFormat","JSON","resultTemplate","{{node_code_1.output}}"));
        assertThat(execute(id,"{\"value\":7}").path("record").path("finalOutput").path("node_output_1").path("value").asInt()).isEqualTo(7);
    }
    @Test void streamContainsOrderedEventsAndCommittedDetail() throws Exception {
        long id = generatedId("代码测试建议");
        MvcResult initial = mvc.perform(post("/api/v1/engine/execute/stream").header("Authorization","Bearer " + token).contentType("application/json")
            .content(json.writeValueAsString(payload(id,"SQL 代码测试"))))
            .andExpect(request().asyncStarted()).andReturn();
        initial.getAsyncResult(15000);
        var response = mvc.perform(asyncDispatch(initial)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/event-stream")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(response).contains("event:WORKFLOW_START","event:NODE_START","event:NODE_TOKEN","event:NODE_COMPLETE","event:WORKFLOW_COMPLETE");
        assertThat(response.indexOf("event:WORKFLOW_START")).isLessThan(response.indexOf("event:NODE_TOKEN"));
        assertThat(response.lastIndexOf("event:NODE_COMPLETE")).isLessThan(response.indexOf("event:WORKFLOW_COMPLETE"));
        assertThat(jdbc.queryForObject("SELECT exec_status FROM wf_exec_record",String.class)).isEqualTo("SUCCESS");
    }
    @Test void queryCombinesAllFiltersAndEnforcesLogPrivacy() throws Exception {
        long id = generatedId("回答问题"); var execution = execute(id,"hello"); long record = execution.path("record").path("id").asLong();
        mvc.perform(get("/api/v1/engine/records").header("Authorization","Bearer " + token).param("workflowId",Long.toString(id)).param("execStatus","SUCCESS")
            .param("triggerMode","SANDBOX").param("minDuration","0").param("maxDuration","999999").param("startTime","2020-01-01 00:00:00").param("endTime","2099-01-01 00:00:00"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/v1/engine/records").header("Authorization","Bearer " + outsider)).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/engine/records/{id}",record).header("Authorization","Bearer " + outsider)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/engine/records/{id}",record).header("Authorization","Bearer " + admin)).andExpect(status().isOk()).andExpect(jsonPath("$.data.nodeLogs.length()").value(3));
        mvc.perform(get("/api/v1/engine/records/99999999").header("Authorization","Bearer " + token)).andExpect(status().isNotFound());
    }
    @Test void overrideRunsSnapshotWithoutChangingStoredCanvasAndRequiresAccess() throws Exception {
        JsonNode snapshot = generate("清洗文本后回答"); long id = snapshot.path("workflow").path("id").asLong();
        Map<String,Object> request = new HashMap<>(payload(id,"hello")); request.put("canvasOverride",Map.of("nodes",snapshot.path("nodes"),"edges",snapshot.path("edges")));
        jdbc.update("UPDATE wf_node_edge SET config_json='{}' WHERE workflow_id=?",id);
        mvc.perform(post("/api/v1/engine/execute/sync").header("Authorization","Bearer " + token).contentType("application/json").content(json.writeValueAsString(request)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.record.execStatus").value("SUCCESS"));
        assertThat(jdbc.queryForObject("SELECT config_json FROM wf_node_edge WHERE workflow_id=? AND element_key='node_input_1'",String.class,id)).isEqualTo("{}");
        mvc.perform(post("/api/v1/engine/execute/sync").header("Authorization","Bearer " + outsider).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isForbidden());
    }
    @Test void marketFiltersSortsAndRejectsPrivateTemplates() throws Exception {
        long first = generatedId("Java 规约检索"), second = generatedId("Java 简单回答");
        jdbc.update("UPDATE wf_workflow SET is_public=1,tags='Java,RAG',fork_count=9,like_count=1 WHERE id=?",first);
        jdbc.update("UPDATE wf_workflow SET is_public=1,tags='Java',fork_count=1,like_count=9 WHERE id=?",second);
        mvc.perform(get("/api/v1/market/templates").param("keyword","Java").param("category","CODE_ASSIST").param("tag","RAG").param("minNodeCount","3").param("maxNodeCount","10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.records[0].id").value(first));
        mvc.perform(get("/api/v1/market/templates").param("sortBy","likes")).andExpect(jsonPath("$.data.records[0].id").value(second));
        mvc.perform(get("/api/v1/market/templates").param("sortBy","popular")).andExpect(jsonPath("$.data.records[0].id").value(first));
        jdbc.update("UPDATE wf_workflow SET is_public=0 WHERE id=?",first);
        mvc.perform(get("/api/v1/market/templates/{id}",first)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/market/templates/{id}/like",first).header("Authorization","Bearer " + token)).andExpect(status().isNotFound());
    }
    @Test void forkClonesEveryChildAndResetsOwnershipPrivacyAndCounters() throws Exception {
        JsonNode source = generate("清洗后如果Java则检索知识库并回答"); long id = source.path("workflow").path("id").asLong();
        jdbc.update("UPDATE wf_workflow SET is_public=1,like_count=12 WHERE id=?",id);
        mvc.perform(post("/api/v1/market/templates/{id}/like",id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/market/templates/{id}/like",id).header("Authorization","Bearer " + token)).andExpect(status().isOk());
        long fork = body(mvc.perform(post("/api/v1/market/templates/{id}/fork",id).header("Authorization","Bearer " + outsider)).andExpect(status().isOk()).andReturn()).path("data").path("newWorkflowId").asLong();
        var cloned = workflows.selectById(fork); assertThat(cloned.getCreatorId()).isEqualTo(5); assertThat(cloned.getIsPublic()).isZero();
        assertThat(cloned.getLikeCount()).isZero(); assertThat(cloned.getForkCount()).isZero(); assertThat(cloned.getCollaboratorIds()).isEmpty();
        assertThat(workflows.selectById(id).getForkCount()).isEqualTo(1); assertThat(workflows.selectById(id).getLikeCount()).isEqualTo(13);
        var a = jdbc.queryForList("SELECT element_key,element_type,node_type,position_json,config_json,source_key,target_key,source_handle FROM wf_node_edge WHERE workflow_id=? ORDER BY element_key",id);
        var b = jdbc.queryForList("SELECT element_key,element_type,node_type,position_json,config_json,source_key,target_key,source_handle FROM wf_node_edge WHERE workflow_id=? ORDER BY element_key",fork);
        assertThat(b).isEqualTo(a); assertThat(cloned.getNodeCount()).isEqualTo(source.path("nodes").size());
        jdbc.update("DELETE FROM wf_workflow WHERE id=?",fork); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_node_edge WHERE workflow_id=?",Long.class,fork)).isZero();
    }
    @Test void forkRollsBackMasterChildrenAndCounterOnChildInsertFailure() throws Exception {
        long id = generatedId("回答问题"); jdbc.update("UPDATE wf_workflow SET is_public=1 WHERE id=?",id);
        long before = count("wf_node_edge"); doThrow(new IllegalStateException("insert failed")).when(elements).insert(any(WfNodeEdge.class));
        assertThatThrownBy(() -> market.fork(id,user)).isInstanceOf(IllegalStateException.class);
        assertThat(count("wf_workflow")).isEqualTo(1); assertThat(count("wf_node_edge")).isEqualTo(before); assertThat(workflows.selectById(id).getForkCount()).isZero();
    }
    @Test void executionPersistenceFailureRollsBackBillingMasterAndLogs() throws Exception {
        long id = generatedId("回答问题"); var req = new WorkflowExecuteReqDTO(); req.setWorkflowId(id); req.setInputPayload(Map.of("text","hello"));
        var realLogs = sqlSession.getMapper(WfExecNodeLogMapper.class);
        doAnswer(invocation -> {
            com.flowmind.platform.engine.entity.WfExecNodeLog row = invocation.getArgument(0);
            if ("OUTPUT".equals(row.getNodeType())) throw new IllegalStateException("log failed after model audit");
            return realLogs.insert(row);
        }).when(logs).insert(any(com.flowmind.platform.engine.entity.WfExecNodeLog.class));
        assertThatThrownBy(() -> service.executeSync(req,user)).isInstanceOf(IllegalStateException.class);
        assertThat(count("wf_exec_record")).isZero(); assertThat(count("wf_exec_node_log")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_model_audit_log WHERE module_name='ENGINE'",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT token_quota FROM sys_user WHERE id=4",Integer.class)).isEqualTo(50000);
    }
    @Test void generationAndQueryValidateInputsAndUnauthenticatedAccess() throws Exception {
        mvc.perform(post("/api/v1/engine/text-to-flow").header("Authorization","Bearer " + token).contentType("application/json").content("{\"prompt\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/engine/records").header("Authorization","Bearer " + token).param("pageSize","1001")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/market/templates").param("sortBy","unsafe_sql")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/market/templates").param("minNodeCount","9").param("maxNodeCount","1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/engine/records")).andExpect(status().isUnauthorized());
    }
    @Test void concurrentExecutionsDoNotLoseQuotaUpdates() throws Exception {
        long id = generatedId("回答问题"); var req = new WorkflowExecuteReqDTO(); req.setWorkflowId(id); req.setInputPayload(Map.of("text","hello"));
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(() -> service.executeSync(req,user)); var second = pool.submit(() -> service.executeSync(req,user));
            int tokens = first.get(20,TimeUnit.SECONDS).record().getTotalTokens() + second.get(20,TimeUnit.SECONDS).record().getTotalTokens();
            assertThat(jdbc.queryForObject("SELECT token_quota FROM sys_user WHERE id=4",Integer.class)).isEqualTo(50000-tokens);
            assertThat(jdbc.queryForObject("SELECT token_used FROM sys_user WHERE id=4",Integer.class)).isEqualTo(tokens);
        }
    }
    @Test void remoteDeepSeekAndQwenParseChunksUsageAndAuth() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); var requestBody = new AtomicReference<String>();
        server.createContext("/v1/chat/completions",exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-key");
            String data = "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"世界\"},\"finish_reason\":\"stop\"}]}\n\ndata: {\"choices\":[],\"usage\":{\"total_tokens\":17}}\n\ndata: [DONE]\n\n";
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type","text/event-stream"); exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        try {
            for (String provider : List.of("DEEPSEEK","QWEN")) {
                var env = new MockEnvironment().withProperty("flowmind.llm."+provider.toLowerCase()+".api-key","test-key")
                    .withProperty("flowmind.llm."+provider.toLowerCase()+".base-url","http://127.0.0.1:"+server.getAddress().getPort()+"/v1");
                var adapter = new LlmClientAdapter(json,env); var chunks = new ArrayList<String>();
                try {
                    var result = adapter.complete(provider,0.5,"sys","hello",1000,chunks::add);
                    assertThat(chunks).containsExactly("你好","世界"); assertThat(result.tokens()).isEqualTo(17); assertThat(result.provider()).isEqualTo(provider);
                    assertThat(json.readTree(requestBody.get()).path("stream").asBoolean()).isTrue();
                } finally { closeAdapter(adapter); }
            }
        } finally { server.stop(0); }
    }

    private void saveModelConfig(String provider, String base, String key, boolean fallback) throws Exception {
        var response = body(mvc.perform(put("/api/v1/admin/models/config").header("Authorization", "Bearer " + admin)
            .contentType("application/json").content(json.writeValueAsString(Map.of(
                "activeProvider", provider, "baseUrl", base, "apiKeyPlain", key,
                "modelName", "configured-" + provider.toLowerCase(Locale.ROOT), "enableMockFallback", fallback,
                "sensitiveWords", List.of("drop table")))))
            .andExpect(status().isOk()).andReturn());
        if (!key.isEmpty()) assertThat(response.toString()).doesNotContain(key);
    }

    @Test void administratorConfigControlsRealTransportAndUpdatesWithoutRestart() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var requests = new ArrayList<String>();
        server.createContext("/", exchange -> {
            synchronized (requests) {
                requests.add(exchange.getRequestURI().getPath());
                requests.add(exchange.getRequestHeaders().getFirst("Authorization"));
                requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            byte[] bytes = ("data: {\"choices\":[{\"delta\":{\"content\":\"configured reply\"},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: {\"choices\":[],\"usage\":{\"total_tokens\":17}}\n\ndata: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            long id = generatedId("回答问题");
            for (String provider : List.of("DEEPSEEK", "QWEN")) {
                String key = "test-admin-key-" + provider;
                saveModelConfig(provider, "http://127.0.0.1:" + server.getAddress().getPort() + "/" + provider, key, false);
                JsonNode result = execute(id, "hello");
                assertThat(result.path("record").path("execStatus").asText()).isEqualTo("SUCCESS");
                assertThat(result.path("record").path("finalOutput").path("report").asText()).isEqualTo("configured reply");
                assertThat(result.path("record").path("totalTokens").asInt()).isEqualTo(17);
                assertThat(jdbc.queryForObject("SELECT model_provider FROM sys_model_audit_log WHERE action_type='EXEC_AI' ORDER BY id DESC LIMIT 1", String.class)).isEqualTo(provider);
                synchronized (requests) {
                    assertThat(requests.get(requests.size() - 3)).isEqualTo("/" + provider + "/chat/completions");
                    assertThat(requests.get(requests.size() - 2)).isEqualTo("Bearer " + key);
                    assertThat(json.readTree(requests.getLast()).path("model").asText()).isEqualTo("configured-" + provider.toLowerCase(Locale.ROOT));
                }
            }
            saveModelConfig("MOCK_LOCAL", "http://127.0.0.1:" + server.getAddress().getPort(), "", false);
            assertThat(execute(id, "hello").path("record").path("finalOutput").path("report").asText()).contains("本地沙箱");
            assertThat(requests).hasSize(6);
        } finally { server.stop(0); }
    }

    @Test void administratorFallbackSettingIsEnforcedAndMissingKeyRemainsOfflineCapable() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.start();
        try {
            long id = generatedId("回答问题");
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
            saveModelConfig("DEEPSEEK", base, "test-admin-key", false);
            var failed = execute(id, "hello");
            assertThat(failed.path("record").path("execStatus").asText()).isEqualTo("FAILED");
            assertThat(failed.path("record").path("totalTokens").asInt()).isZero();
            saveModelConfig("DEEPSEEK", base, "", true);
            assertThat(execute(id, "hello").path("record").path("finalOutput").path("report").asText()).contains("本地沙箱");
            jdbc.update("DELETE FROM sys_model_audit_log WHERE action_type='MODEL_CONFIG'");
            saveModelConfig("QWEN", base, "", false);
            assertThat(execute(id, "hello").path("record").path("execStatus").asText()).isEqualTo("SUCCESS");
        } finally { server.stop(0); }
    }
    @Test void remoteFailureFallsBackLocallyButPartialStreamNeverDuplicatesOutput() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange -> { exchange.sendResponseHeaders(503,-1); exchange.close(); }); server.start();
        var env = new MockEnvironment().withProperty("flowmind.llm.deepseek.api-key","test-key").withProperty("flowmind.llm.mock-chunk-delay-ms","0")
            .withProperty("flowmind.llm.deepseek.base-url","http://127.0.0.1:"+server.getAddress().getPort()+"/v1");
        var adapter = new LlmClientAdapter(json,env);
        try {
            var chunks = new ArrayList<String>(); var result = adapter.complete("DEEPSEEK",0.5,"sys","SQL漏洞",10000,chunks::add);
            assertThat(result.provider()).isEqualTo("MOCK_LOCAL"); assertThat(String.join("",chunks)).isEqualTo(result.output()).contains("PreparedStatement");
            server.removeContext("/v1/chat/completions");
            server.createContext("/v1/chat/completions",exchange -> {
                byte[] data = "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,data.length); exchange.getResponseBody().write(data); exchange.close();
            });
            chunks.clear(); assertThatThrownBy(() -> adapter.complete("DEEPSEEK",0.5,"sys","hello",10000,chunks::add)).isInstanceOf(BusinessException.class);
            assertThat(chunks).containsExactly("partial");
        } finally { closeAdapter(adapter); server.stop(0); }
    }
    private void closeAdapter(LlmClientAdapter adapter) throws Exception {
        adapter.close();
    }
}
