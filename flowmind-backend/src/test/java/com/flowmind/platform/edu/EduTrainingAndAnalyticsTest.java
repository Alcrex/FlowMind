package com.flowmind.platform.edu;

import com.fasterxml.jackson.databind.*;
import com.flowmind.platform.common.security.*;
import com.flowmind.platform.edu.mapper.EduTaskMapper;
import com.flowmind.platform.edu.service.EduTrainingService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:flowmind_edu_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SqlGroup({@Sql(statements="DROP ALL OBJECTS"), @Sql(scripts="/schema-test.sql")})
class EduTrainingAndAnalyticsTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;
    @Autowired EduTrainingService service;
    @SpyBean EduTaskMapper tasks;
    String student, otherStudent, teacher, otherTeacher, admin;

    @BeforeEach void setup() {
        reset(tasks);
        jdbc.update("DELETE FROM edu_submission");
        jdbc.update("INSERT INTO sys_user(id,username,password_hash,real_name,role_type,dept_class) VALUES(7,'teacher_other','hash','其他教师','TEACHER','计算机系'),(8,'other_class','hash','外班同学','STUDENT','计科2班')");
        student = jwt.generateToken(5L,"haotangxin","郝唐鑫","STUDENT");
        otherStudent = jwt.generateToken(4L,"zhangyucheng","张誉诚","STUDENT");
        teacher = jwt.generateToken(2L,"teacher_litian","李甜","TEACHER");
        otherTeacher = jwt.generateToken(7L,"teacher_other","其他教师","TEACHER");
        admin = jwt.generateToken(1L,"admin","管理员","ADMIN");
    }
    MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder req, String token) { return req.header("Authorization", "Bearer " + token); }
    MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder req, Object data) throws Exception { return req.contentType("application/json").content(json.writeValueAsString(data)); }
    JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
    JsonNode submit(String token, long taskId, long workflowId) throws Exception {
        return data(mvc.perform(body(as(post("/api/v1/edu/submissions"),token),Map.of("taskId",taskId,"workflowId",workflowId,"studentReport","通过知识检索和大模型构建代码评审流程，验证输入、输出与错误处理。"))));
    }
    Map<String,Object> taskPayload(String title) throws Exception {
        return new LinkedHashMap<>(Map.of("taskTitle",title,"courseName","Web实训","targetClass","计科1班","taskRequirement","设计完整的知识增强工作流并提交报告。",
            "requiredNodesJson",Map.of("minNodes",4,"mustInclude",List.of("INPUT","KNOWLEDGE","LLM","OUTPUT")),
            "rubricJson",List.of(Map.of("item","拓扑设计","maxScore",30),Map.of("item","Prompt质量","maxScore",40),Map.of("item","实训报告","maxScore",30)),
            "deadline","2099-11-15 23:59:59","taskStatus",1));
    }
    Map<String,Object> gradePayload() {
        return Map.of("score",95,"teacherComment","拓扑完整，知识增强合理，报告清晰。","reviewStatus",1,
            "rubricDetailJson",Map.of("拓扑完整性与节点规范",29,"Prompt工程与知识库结合度",38,"沙箱运行效果与实验报告",28));
    }
    long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table,Long.class); }

    @Test void snapshotPreservesFullNodesEdgesConfigurationAndWorkflowAfterOriginalChanges() throws Exception {
        var s = submit(student,1,1); long id = s.path("id").asLong();
        assertThat(s.path("autoCheckPassed").asInt()).isEqualTo(1);
        assertThat(s.path("isLate").asInt()).isZero();
        assertThat(s.path("canvasSnapshotJson").path("nodes")).hasSize(4);
        assertThat(s.path("canvasSnapshotJson").path("edges")).hasSize(3);
        assertThat(s.path("canvasSnapshotJson").path("nodes").get(2).path("configJson").path("systemPrompt").asText()).isNotBlank();
        assertThat(s.path("canvasSnapshotJson").path("workflow").path("versionTag").asText()).isEqualTo("v1.2.0");
        jdbc.update("DELETE FROM wf_node_edge WHERE workflow_id=1");
        jdbc.update("UPDATE wf_workflow SET wf_name='修改后的工作流',version_tag='v9.0.0' WHERE id=1");
        var detail = data(mvc.perform(as(get("/api/v1/edu/submissions/{id}",id),teacher)));
        assertThat(detail.path("canvasSnapshotJson")).isEqualTo(s.path("canvasSnapshotJson"));
    }
    @Test void missingNodeTypesAndMinimumCountsWarnButNeverRejectSubmission() throws Exception {
        jdbc.update("DELETE FROM wf_node_edge WHERE workflow_id=1 AND node_type IN ('LLM','KNOWLEDGE')");
        var s = submit(student,1,1); assertThat(s.path("autoCheckPassed").asInt()).isZero(); assertThat(count("edu_submission")).isEqualTo(1);
        jdbc.update("UPDATE edu_task SET required_nodes_json=? WHERE id=1", "{\"minNodes\":1,\"mustInclude\":[\"LLM\"]}");
        assertThat(submit(student,1,1).path("autoCheckPassed").asInt()).isZero();
        jdbc.update("UPDATE edu_task SET required_nodes_json=? WHERE id=1", "{\"minNodes\":2,\"mustInclude\":[\"INPUT\",\"OUTPUT\"]}");
        assertThat(submit(student,1,1).path("autoCheckPassed").asInt()).isEqualTo(1);
    }
    @Test void resubmissionUpdatesUniqueRowAndClearsAllPriorGradingFields() throws Exception {
        long id = submit(student,1,1).path("id").asLong();
        data(mvc.perform(body(as(put("/api/v1/edu/submissions/{id}/grade",id),teacher),gradePayload())));
        jdbc.update("UPDATE wf_workflow SET version_tag='v2.0.0' WHERE id=1");
        var updated = submit(student,1,1);
        assertThat(updated.path("id").asLong()).isEqualTo(id); assertThat(count("edu_submission")).isEqualTo(1);
        assertThat(updated.path("versionTag").asText()).isEqualTo("v2.0.0"); assertThat(updated.path("reviewStatus").asInt()).isZero();
        for (String field : List.of("score","teacherComment","rubricDetailJson","reviewedAt")) assertThat(updated.path(field).isNull()).as(field).isTrue();
        assertThat(jdbc.queryForObject("SELECT score FROM edu_submission WHERE id=?",Object.class,id)).isNull();
    }
    @Test void overdueStatusIsConsistentAcrossTaskFiltersAndSubmission() throws Exception {
        jdbc.update("UPDATE edu_task SET deadline='2000-01-01 00:00:00',task_status=1 WHERE id=1");
        assertThat(submit(student,1,1).path("isLate").asInt()).isEqualTo(1);
        var page = data(mvc.perform(as(get("/api/v1/edu/tasks").param("taskStatus","2"),student)));
        assertThat(page.path("total").asInt()).isEqualTo(1); assertThat(page.path("records").get(0).path("taskStatus").asInt()).isEqualTo(2);
        assertThat(data(mvc.perform(as(get("/api/v1/edu/tasks").param("taskStatus","1"),student))).path("total").asInt()).isZero();
    }
    @Test void archivedTaskCannotOverwriteExistingSubmission() throws Exception {
        var before = submit(student,1,1); jdbc.update("UPDATE edu_task SET task_status=0 WHERE id=1");
        mvc.perform(body(as(post("/api/v1/edu/submissions"),student),Map.of("taskId",1,"workflowId",1,"studentReport","不应覆盖")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        assertThat(data(mvc.perform(as(get("/api/v1/edu/submissions/{id}",before.path("id").asLong()),student))).path("studentReport")).isEqualTo(before.path("studentReport"));
    }
    @Test void taskCrudFiltersAndCascadeDeleteWorkAgainstDatabase() throws Exception {
        var payload = taskPayload("测试多分支课题");
        long id = data(mvc.perform(body(as(post("/api/v1/edu/tasks"),teacher),payload))).path("id").asLong();
        assertThat(data(mvc.perform(as(get("/api/v1/edu/tasks").param("keyword","多分支").param("courseName","Web").param("targetClass","计科1班").param("taskStatus","1").param("pageSize","1"),teacher))).path("total").asInt()).isEqualTo(1);
        payload.put("taskTitle","修改课题"); payload.put("rubricJson",null);
        var updated = data(mvc.perform(body(as(put("/api/v1/edu/tasks/{id}",id),teacher),payload)));
        assertThat(updated.path("taskTitle").asText()).isEqualTo("修改课题");
        assertThat(data(mvc.perform(as(get("/api/v1/edu/tasks/{id}",id),teacher))).path("rubricJson").isNull()).isTrue();
        submit(student,id,1);
        data(mvc.perform(as(delete("/api/v1/edu/tasks/{id}",id),teacher)));
        assertThat(count("edu_submission")).isZero();
        mvc.perform(as(get("/api/v1/edu/tasks/{id}",id),teacher)).andExpect(status().isNotFound());
        assertThat(count("wf_workflow")).isEqualTo(1);
    }
    @Test void deleteRollsBackChildrenIfParentDeletionFails() throws Exception {
        submit(student,1,1);
        doThrow(new IllegalStateException("parent deletion failed")).when(tasks).deleteById(1L);
        assertThatThrownBy(() -> service.deleteTask(1L,new LoginUser(2L,"teacher_litian","李甜","TEACHER",""))).isInstanceOf(IllegalStateException.class);
        assertThat(count("edu_submission")).isEqualTo(1); assertThat(count("edu_task")).isEqualTo(1);
    }
    @Test void submissionCombinedFiltersPaginationAndStudentPrivacy() throws Exception {
        long id = submit(student,1,1).path("id").asLong(); submit(otherStudent,1,1);
        var query = get("/api/v1/edu/submissions").param("taskId","1").param("targetClass","计科1班").param("reviewStatus","0").param("isLate","0").param("autoCheckPassed","1").param("studentKeyword","郝唐鑫");
        assertThat(data(mvc.perform(as(query,teacher))).path("total").asInt()).isEqualTo(1);
        data(mvc.perform(body(as(put("/api/v1/edu/submissions/{id}/grade",id),teacher),gradePayload())));
        var page = data(mvc.perform(as(get("/api/v1/edu/submissions").param("minScore","90").param("maxScore","100").param("reviewStatus","1").param("studentKeyword","haotangxin"),teacher)));
        assertThat(page.path("total").asInt()).isEqualTo(1); assertThat(page.path("records").get(0).path("score").asDouble()).isEqualTo(95);
        assertThat(data(mvc.perform(as(get("/api/v1/edu/submissions").param("pageSize","1").param("pageNum","2"),teacher))).path("records")).hasSize(1);
        assertThat(data(mvc.perform(as(get("/api/v1/edu/submissions"),student))).path("total").asInt()).isEqualTo(1);
        mvc.perform(as(get("/api/v1/edu/submissions/{id}",id),otherStudent)).andExpect(status().isForbidden());
        assertThat(data(mvc.perform(as(get("/api/v1/edu/submissions").param("studentKeyword","' OR 1=1 --"),teacher))).path("total").asInt()).isZero();
    }
    @Test void gradeValidatesRubricTotalLimitsAndReturnForRedoClearsScore() throws Exception {
        long id = submit(student,1,1).path("id").asLong(); var payload = new HashMap<>(gradePayload());
        payload.put("score",101);
        mvc.perform(body(as(put("/api/v1/edu/submissions/{id}/grade",id),teacher),payload)).andExpect(status().isBadRequest());
        payload.put("score",94);
        mvc.perform(body(as(put("/api/v1/edu/submissions/{id}/grade",id),teacher),payload)).andExpect(status().isBadRequest());
        payload.put("rubricDetailJson",Map.of("拓扑完整性与节点规范",31,"Prompt工程与知识库结合度",35,"沙箱运行效果与实验报告",28));
        mvc.perform(body(as(put("/api/v1/edu/submissions/{id}/grade",id),teacher),payload)).andExpect(status().isBadRequest());
        var graded = data(mvc.perform(body(as(put("/api/v1/edu/submissions/{id}/grade",id),teacher),gradePayload())));
        assertThat(graded.path("score").asDouble()).isEqualTo(95); assertThat(graded.path("reviewStatus").asInt()).isEqualTo(1); assertThat(graded.path("reviewedAt").isNull()).isFalse();
        var redo = data(mvc.perform(body(as(put("/api/v1/edu/submissions/{id}/grade",id),teacher),Map.of("reviewStatus",2,"teacherComment","请补充测试后重新提交"))));
        assertThat(redo.path("reviewStatus").asInt()).isEqualTo(2); assertThat(redo.path("score").isNull()).isTrue();
        assertThat(data(mvc.perform(as(get("/api/v1/edu/analytics"),teacher))).path("summary").path("gradedCount").asInt()).isZero();
    }
    @Test void rolesResourceOwnershipAndClassRestrictionsAreEnforced() throws Exception {
        mvc.perform(get("/api/v1/edu/tasks")).andExpect(status().isUnauthorized());
        mvc.perform(body(as(post("/api/v1/edu/tasks"),student),taskPayload("非法"))).andExpect(status().isForbidden());
        mvc.perform(as(delete("/api/v1/edu/tasks/1"),otherTeacher)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/v1/edu/tasks/1"),otherTeacher)).andExpect(status().isForbidden());
        String outsider = jwt.generateToken(8L,"other_class","外班同学","STUDENT");
        assertThat(data(mvc.perform(as(get("/api/v1/edu/tasks"),outsider))).path("total").asInt()).isZero();
        mvc.perform(body(as(post("/api/v1/edu/submissions"),outsider),Map.of("taskId",1,"workflowId",1,"studentReport","越班提交"))).andExpect(status().isForbidden());
        jdbc.update("UPDATE wf_workflow SET creator_id=3,collaborator_ids='[]',is_public=1 WHERE id=1");
        mvc.perform(body(as(post("/api/v1/edu/submissions"),student),Map.of("taskId",1,"workflowId",1,"studentReport","不能提交他人公开工作流"))).andExpect(status().isForbidden());
    }
    @Test void teachersCannotReadGradeOrAnalyzeOtherTeachersSubmissionsButAdminCan() throws Exception {
        long id = submit(student,1,1).path("id").asLong();
        assertThat(data(mvc.perform(as(get("/api/v1/edu/submissions"),otherTeacher))).path("total").asInt()).isZero();
        mvc.perform(as(get("/api/v1/edu/submissions/{id}",id),otherTeacher)).andExpect(status().isForbidden());
        mvc.perform(body(as(put("/api/v1/edu/submissions/{id}/grade",id),otherTeacher),gradePayload())).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/v1/edu/analytics").param("taskId","1"),otherTeacher)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/v1/edu/analytics"),student)).andExpect(status().isForbidden());
        data(mvc.perform(body(as(put("/api/v1/edu/submissions/{id}/grade",id),admin),gradePayload())));
        assertThat(data(mvc.perform(as(get("/api/v1/edu/analytics"),admin))).path("summary").path("gradedCount").asInt()).isEqualTo(1);
    }
    @Test void invalidBodiesRulesRubricsAndQueryRangesUseStandardErrors() throws Exception {
        mvc.perform(body(as(post("/api/v1/edu/submissions"),student),Map.of("taskId",1,"workflowId",1,"studentReport"," "))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        for (Object rule : List.of(Map.of("minNodes",-1,"mustInclude",List.of()),Map.of("minNodes",1,"mustInclude",List.of("ALIEN")),Map.of("minNodes",1.5,"mustInclude",List.of()))) {
            var payload = taskPayload("无效规则"); payload.put("requiredNodesJson",rule);
            mvc.perform(body(as(post("/api/v1/edu/tasks"),teacher),payload)).andExpect(status().isBadRequest());
        }
        var invalidRubric = taskPayload("无效细则"); invalidRubric.put("rubricJson",List.of(Map.of("item","重复","maxScore",50),Map.of("item","重复","maxScore",50)));
        mvc.perform(body(as(post("/api/v1/edu/tasks"),teacher),invalidRubric)).andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/v1/edu/submissions").param("minScore","90").param("maxScore","50"),teacher)).andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/v1/edu/tasks").param("pageSize","101"),teacher)).andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/v1/edu/tasks/999999"),teacher)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(40004));
    }
    @Test void analyticsAggregatesScoresRadarTrendsAndRealNodeErrorsWithoutSharedWorkflowDuplication() throws Exception {
        long first = submit(student,1,1).path("id").asLong(), second = submit(otherStudent,1,1).path("id").asLong();
        data(mvc.perform(body(as(put("/api/v1/edu/submissions/{id}/grade",first),teacher),gradePayload())));
        jdbc.update("UPDATE edu_submission SET score=59.99,review_status=1,submitted_at='2026-10-29 10:00:00' WHERE id=?",second);
        jdbc.update("UPDATE edu_submission SET submitted_at='2026-10-30 10:00:00' WHERE id=?",first);
        // One student's workflow can be submitted to several tasks; correlated EXISTS counts its run only once.
        long another = data(mvc.perform(body(as(post("/api/v1/edu/tasks"),teacher),taskPayload("另一个课题")))).path("id").asLong();
        submit(student,another,1);
        jdbc.update("INSERT INTO wf_exec_record(id,exec_no,workflow_id,trigger_user_id,trigger_mode,exec_status,started_at) VALUES(100,'EDU-FAILED',1,5,'SANDBOX','FAILED','2026-10-30 09:00:00'),(101,'EDU-SUCCESS',1,4,'SANDBOX','SUCCESS','2026-10-29 09:00:00'),(102,'TEACHER-REPLAY',1,2,'TEACHER_REVIEW','FAILED','2026-10-30 09:00:00'),(103,'UNRELATED-USER',1,6,'SANDBOX','FAILED','2026-10-30 09:00:00')");
        jdbc.update("INSERT INTO wf_exec_node_log(exec_record_id,node_key,node_name,node_type,step_status) VALUES(100,'llm','模型','LLM','ERROR'),(100,'code','脚本','CODE','ERROR'),(102,'llm','教师试跑','LLM','ERROR'),(103,'llm','其他学生','LLM','ERROR')");
        var a = data(mvc.perform(as(get("/api/v1/edu/analytics").param("taskId","1").param("targetClass","计科1班"),teacher)));
        assertThat(a.path("summary").path("totalSubmissions").asInt()).isEqualTo(2);
        assertThat(a.path("summary").path("gradedCount").asInt()).isEqualTo(2);
        assertThat(a.path("summary").path("passRate").asDouble()).isEqualTo(50);
        assertThat(a.path("summary").path("avgScore").asDouble()).isEqualTo(77.5);
        assertThat(a.path("scoreBuckets")).hasSize(5); assertThat(a.path("scoreBuckets").get(0).path("count").asInt()).isEqualTo(1);
        assertThat(a.path("scoreBuckets").get(4).path("count").asInt()).isEqualTo(1);
        assertThat(a.path("radarDimensions")).hasSize(5);
        assertThat(a.path("radarDimensions").get(0).path("avgRate").asDouble()).isEqualTo(100);
        assertThat(a.path("radarDimensions").get(1).path("avgRate").asDouble()).isEqualTo(100);
        assertThat(a.path("radarDimensions").get(2).path("avgRate").asDouble()).isZero();
        assertThat(a.path("radarDimensions").get(3).path("avgRate").asDouble()).isEqualTo(50);
        assertThat(a.path("radarDimensions").get(4).path("avgRate").asDouble()).isEqualTo(93.33);
        assertThat(a.path("errorNodeDistribution")).hasSize(2);
        for (var e : a.path("errorNodeDistribution")) assertThat(e.path("errorCount").asInt()).isEqualTo(1);
        assertThat(a.path("dailySubmissionTrend")).hasSize(2);
        assertThat(a.path("dailySubmissionTrend").get(0).path("date").asText()).isEqualTo("2026-10-29");
        int debugCount = 0; for (var d : a.path("dailySubmissionTrend")) debugCount += d.path("debugCount").asInt();
        assertThat(debugCount).isEqualTo(2);
        var all = data(mvc.perform(as(get("/api/v1/edu/analytics"),teacher)));
        int allDebug = 0; for (var d : all.path("dailySubmissionTrend")) allDebug += d.path("debugCount").asInt();
        assertThat(allDebug).isEqualTo(2);
    }
    @Test void emptyAnalyticsHasFiveZeroBucketsAndDimensionsAndNoNan() throws Exception {
        var a = data(mvc.perform(as(get("/api/v1/edu/analytics").param("targetClass","不存在的班级"),teacher)));
        assertThat(a.path("summary").path("totalSubmissions").asInt()).isZero();
        assertThat(a.path("summary").path("avgScore").asDouble()).isZero(); assertThat(a.path("summary").path("passRate").asDouble()).isZero();
        assertThat(a.path("scoreBuckets")).hasSize(5); assertThat(a.path("radarDimensions")).hasSize(5);
        for (var d : a.path("radarDimensions")) assertThat(d.path("avgRate").asDouble()).isZero();
        assertThat(a.path("dailySubmissionTrend")).isEmpty(); assertThat(a.path("errorNodeDistribution")).isEmpty();
    }
    @Test void scoreBucketBoundaryValuesRemainDisjointAndPendingScoresAreExcluded() throws Exception {
        for (long studentId : List.of(3L,4L,5L,6L,8L)) jdbc.update("INSERT INTO edu_submission(task_id,student_id,workflow_id,canvas_snapshot_json,student_report,score,review_status) VALUES(1,?,1,'{\"nodes\":[],\"edges\":[]}','测试',?,1)", studentId, switch((int)studentId) { case 3 -> 59.99; case 4 -> 60; case 5 -> 70; case 6 -> 80; default -> 100; });
        var a = data(mvc.perform(as(get("/api/v1/edu/analytics"),teacher)));
        for (var bucket : a.path("scoreBuckets")) assertThat(bucket.path("count").asInt()).isEqualTo(1);
        assertThat(a.path("summary").path("passRate").asDouble()).isEqualTo(80);
        jdbc.update("UPDATE edu_submission SET review_status=0 WHERE student_id=8");
        a = data(mvc.perform(as(get("/api/v1/edu/analytics"),teacher)));
        assertThat(a.path("scoreBuckets").get(4).path("count").asInt()).isZero(); assertThat(a.path("summary").path("gradedCount").asInt()).isEqualTo(4);
    }
    @Test void teacherExecutesFrozenCanvasAfterLiveNodesAreDeleted() throws Exception {
        var sub = submit(student,1,1); jdbc.update("DELETE FROM wf_node_edge WHERE workflow_id=1");
        var result = data(mvc.perform(body(as(post("/api/v1/engine/execute/sync"),teacher),Map.of("workflowId",1,"triggerMode","TEACHER_REVIEW",
            "inputPayload",Map.of("codeSnippet","public class Demo {}"),"canvasOverride",sub.path("canvasSnapshotJson")))));
        assertThat(result.path("record").path("execStatus").asText()).isEqualTo("SUCCESS"); assertThat(result.path("nodeLogs")).hasSize(4);
        assertThat(count("wf_node_edge")).isZero();
    }
}
