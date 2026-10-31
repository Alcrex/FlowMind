package com.flowmind.platform.edu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowmind.platform.common.api.*;
import com.flowmind.platform.common.exception.BusinessException;
import com.flowmind.platform.common.security.LoginUser;
import com.flowmind.platform.edu.dto.*;
import com.flowmind.platform.edu.entity.*;
import com.flowmind.platform.edu.mapper.*;
import com.flowmind.platform.edu.vo.*;
import com.flowmind.platform.edu.vo.EduAnalyticsVO.*;
import com.flowmind.platform.engine.runtime.DagTopologicalSorter;
import com.flowmind.platform.workflow.entity.*;
import com.flowmind.platform.workflow.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EduTrainingService {
    private static final Set<String> NODE_TYPES = Set.of("INPUT", "OUTPUT", "LLM", "KNOWLEDGE", "CODE", "CONDITION");
    private final EduTaskMapper tasks;
    private final EduSubmissionMapper submissions;
    private final EduReadMapper reads;
    private final WfWorkflowMapper workflows;
    private final WfNodeEdgeMapper elements;
    private final ObjectMapper json;
    private final DagTopologicalSorter sorter;

    private void invalid(String message) { throw new BusinessException(ErrorCode.PARAM_ERROR, message); }
    private boolean admin(LoginUser user) { return "ADMIN".equals(user.roleType()); }
    private void teacher(LoginUser user) {
        if (!admin(user) && !"TEACHER".equals(user.roleType())) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    private EduTask task(Long id, boolean lock) {
        EduTask t = lock ? tasks.selectOne(new LambdaQueryWrapper<EduTask>().eq(EduTask::getId, id).last("FOR UPDATE")) : tasks.selectById(id);
        if (t == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return t;
    }
    private void requireOwner(EduTask t, LoginUser user) {
        teacher(user);
        if (!admin(user) && !user.id().equals(t.getTeacherId())) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    private void requireVisible(EduTask t, LoginUser user) {
        if ("STUDENT".equals(user.roleType())) {
            var account = reads.account(user.id());
            if (account == null || !t.getTargetClass().equals(account.deptClass())) throw new BusinessException(ErrorCode.FORBIDDEN);
        } else requireOwner(t, user);
    }
    private int status(EduTask t) {
        return t.getTaskStatus() == 1 && t.getDeadline().isBefore(LocalDateTime.now()) ? 2 : t.getTaskStatus();
    }
    private EduTaskVO taskVO(EduTask t, LoginUser user) {
        var vo = new EduTaskVO(); BeanUtils.copyProperties(t, vo); vo.setTaskStatus(status(t));
        var account = reads.account(t.getTeacherId()); vo.setTeacherName(account == null ? "" : account.realName());
        if ("STUDENT".equals(user.roleType())) {
            var sub = submissions.selectOne(new LambdaQueryWrapper<EduSubmission>().eq(EduSubmission::getTaskId, t.getId()).eq(EduSubmission::getStudentId, user.id()));
            if (sub != null) { vo.setMySubmissionId(sub.getId()); vo.setMyReviewStatus(sub.getReviewStatus()); vo.setMyScore(sub.getScore()); }
        }
        return vo;
    }
    public PageResult<EduTaskVO> queryTasks(EduTaskQueryDTO dto, LoginUser user) {
        var q = new LambdaQueryWrapper<EduTask>();
        if ("STUDENT".equals(user.roleType())) q.eq(EduTask::getTargetClass, reads.account(user.id()).deptClass());
        else { teacher(user); if (!admin(user)) q.eq(EduTask::getTeacherId, user.id()); }
        if (StringUtils.hasText(dto.getKeyword())) q.and(w -> w.like(EduTask::getTaskTitle, dto.getKeyword()).or().like(EduTask::getTaskCode, dto.getKeyword()));
        q.like(StringUtils.hasText(dto.getCourseName()), EduTask::getCourseName, dto.getCourseName())
            .eq(StringUtils.hasText(dto.getTargetClass()), EduTask::getTargetClass, dto.getTargetClass());
        if (dto.getTaskStatus() != null) {
            var now = LocalDateTime.now();
            switch (dto.getTaskStatus()) {
                case 1 -> q.eq(EduTask::getTaskStatus, 1).ge(EduTask::getDeadline, now);
                case 2 -> q.and(w -> w.eq(EduTask::getTaskStatus, 2).or(a -> a.eq(EduTask::getTaskStatus, 1).lt(EduTask::getDeadline, now)));
                default -> q.eq(EduTask::getTaskStatus, 0);
            }
        }
        q.orderByDesc(EduTask::getCreatedAt).orderByDesc(EduTask::getId);
        var page = tasks.selectPage(new Page<>(dto.getPageNum(), dto.getPageSize()), q);
        return new PageResult<>(page.getRecords().stream().map(t -> taskVO(t, user)).toList(), page.getTotal(), page.getCurrent(), page.getSize());
    }
    public EduTaskVO detailTask(Long id, LoginUser user) {
        var t = task(id, false); requireVisible(t, user); return taskVO(t, user);
    }
    private void validateRules(JsonNode rule) {
        if (rule == null || !rule.isObject() || !rule.path("minNodes").isIntegralNumber() || !rule.path("minNodes").canConvertToInt()
            || rule.path("minNodes").asInt() < 0 || !rule.path("mustInclude").isArray()) invalid("节点规则必须包含非负 minNodes 和 mustInclude 数组");
        for (var type : rule.path("mustInclude")) if (!type.isTextual() || !NODE_TYPES.contains(type.asText())) invalid("不支持的必含节点类型");
    }
    private void validateRubric(JsonNode rubric) {
        if (rubric == null || rubric.isNull()) return;
        if (!rubric.isArray() || rubric.isEmpty()) invalid("评分细则必须是非空数组");
        Set<String> names = new HashSet<>(); BigDecimal total = BigDecimal.ZERO;
        for (var r : rubric) {
            if (!r.path("item").isTextual() || r.path("item").asText().isBlank() || !names.add(r.path("item").asText())
                || !r.path("maxScore").isNumber() || r.path("maxScore").decimalValue().signum() <= 0) invalid("评分项名称须唯一且满分必须大于零");
            total = total.add(r.path("maxScore").decimalValue());
        }
        if (total.compareTo(BigDecimal.valueOf(100)) != 0) invalid("评分细则满分合计必须为100");
    }
    @Transactional(rollbackFor = Exception.class)
    public EduTaskVO saveTask(Long id, EduTaskSaveDTO dto, LoginUser user) {
        teacher(user); validateRules(dto.getRequiredNodesJson()); validateRubric(dto.getRubricJson());
        EduTask t = id == null ? new EduTask() : task(id, true);
        if (id != null) requireOwner(t, user);
        if (id == null) { t.setTeacherId(user.id()); t.setTaskCode("TASK-" + UUID.randomUUID()); t.setCreatedAt(LocalDateTime.now()); }
        BeanUtils.copyProperties(dto, t); t.setUpdatedAt(LocalDateTime.now());
        if (id == null) tasks.insert(t); else tasks.updateById(t);
        log.info("Saved edu task {} by {}", t.getId(), user.id()); return taskVO(t, user);
    }
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTask(Long id, LoginUser user) {
        var t = task(id, true); requireOwner(t, user);
        submissions.delete(new LambdaQueryWrapper<EduSubmission>().eq(EduSubmission::getTaskId, id)); tasks.deleteById(id);
        log.info("Deleted edu task {} and submissions by {}", id, user.id()); return true;
    }
    @Transactional(rollbackFor = Exception.class)
    public EduSubmissionVO submit(SubmissionSubmitDTO dto, LoginUser user) {
        if (!"STUDENT".equals(user.roleType())) throw new BusinessException(ErrorCode.FORBIDDEN);
        // Serialize task deletion, duplicate submissions and grading with this task-level lock.
        var t = task(dto.getTaskId(), true); requireVisible(t, user);
        if (t.getTaskStatus() == 0) invalid("课题已归档，不能提交");
        var w = workflows.selectOne(new LambdaQueryWrapper<WfWorkflow>().eq(WfWorkflow::getId, dto.getWorkflowId()).eq(WfWorkflow::getStatus, 1).last("FOR UPDATE"));
        if (w == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!user.id().equals(w.getCreatorId()) && (w.getCollaboratorIds() == null || !w.getCollaboratorIds().contains(user.id()))) throw new BusinessException(ErrorCode.FORBIDDEN);
        var rows = elements.selectList(new LambdaQueryWrapper<WfNodeEdge>().eq(WfNodeEdge::getWorkflowId, w.getId()).orderByAsc(WfNodeEdge::getId));
        var nodes = rows.stream().filter(n -> "NODE".equals(n.getElementType())).toList();
        ObjectNode snapshot = json.createObjectNode(); snapshot.set("workflow", json.valueToTree(w));
        snapshot.set("nodes", json.valueToTree(nodes)); snapshot.set("edges", json.valueToTree(rows.stream().filter(n -> "EDGE".equals(n.getElementType())).toList()));
        var s = submissions.selectOne(new LambdaQueryWrapper<EduSubmission>().eq(EduSubmission::getTaskId, t.getId()).eq(EduSubmission::getStudentId, user.id()));
        boolean create = s == null; if (create) s = new EduSubmission();
        s.setTaskId(t.getId()); s.setStudentId(user.id()); s.setWorkflowId(w.getId()); s.setVersionTag(w.getVersionTag());
        s.setCanvasSnapshotJson(snapshot); s.setStudentReport(dto.getStudentReport()); s.setSubmittedAt(LocalDateTime.now());
        s.setIsLate(s.getSubmittedAt().isAfter(t.getDeadline()) ? 1 : 0);
        Set<String> types = new HashSet<>(); nodes.forEach(n -> types.add(n.getNodeType()));
        boolean passed = nodes.size() >= t.getRequiredNodesJson().path("minNodes").asInt();
        for (var type : t.getRequiredNodesJson().path("mustInclude")) passed &= types.contains(type.asText());
        s.setAutoCheckPassed(passed ? 1 : 0); s.setReviewStatus(0); s.setScore(null); s.setRubricDetailJson(null); s.setTeacherComment(null); s.setReviewedAt(null);
        if (create) submissions.insert(s); else submissions.updateById(s);
        log.info("Student {} submitted task {}, passed={}, late={}", user.id(), t.getId(), passed, s.getIsLate());
        return submissionVO(s, t);
    }
    private LambdaQueryWrapper<EduSubmission> submissionScope(Long taskId, String targetClass, LoginUser user) {
        var q = new LambdaQueryWrapper<EduSubmission>().eq(taskId != null, EduSubmission::getTaskId, taskId);
        if ("STUDENT".equals(user.roleType())) q.eq(EduSubmission::getStudentId, user.id());
        else {
            teacher(user);
            if (!admin(user)) q.apply("EXISTS (SELECT 1 FROM edu_task t WHERE t.id=edu_submission.task_id AND t.teacher_id={0})", user.id());
        }
        if (StringUtils.hasText(targetClass)) q.apply("EXISTS (SELECT 1 FROM edu_task t WHERE t.id=edu_submission.task_id AND t.target_class={0})", targetClass);
        return q;
    }
    public PageResult<EduSubmissionVO> querySubmissions(SubmissionQueryDTO dto, LoginUser user) {
        if (dto.getMinScore() != null && dto.getMaxScore() != null && dto.getMinScore().compareTo(dto.getMaxScore()) > 0) invalid("最低分不能大于最高分");
        var q = submissionScope(dto.getTaskId(), dto.getTargetClass(), user);
        q.eq(dto.getReviewStatus() != null, EduSubmission::getReviewStatus, dto.getReviewStatus())
            .eq(dto.getIsLate() != null, EduSubmission::getIsLate, dto.getIsLate())
            .eq(dto.getAutoCheckPassed() != null, EduSubmission::getAutoCheckPassed, dto.getAutoCheckPassed())
            .ge(dto.getMinScore() != null, EduSubmission::getScore, dto.getMinScore()).le(dto.getMaxScore() != null, EduSubmission::getScore, dto.getMaxScore());
        if (StringUtils.hasText(dto.getStudentKeyword())) q.apply("EXISTS (SELECT 1 FROM sys_user u WHERE u.id=edu_submission.student_id AND (u.username LIKE {0} OR u.real_name LIKE {0}))", "%" + dto.getStudentKeyword() + "%");
        q.orderByDesc(EduSubmission::getSubmittedAt).orderByDesc(EduSubmission::getId);
        var page = submissions.selectPage(new Page<>(dto.getPageNum(), dto.getPageSize()), q);
        return new PageResult<>(page.getRecords().stream().map(s -> submissionVO(s, task(s.getTaskId(), false))).toList(), page.getTotal(), page.getCurrent(), page.getSize());
    }
    private EduSubmission submission(Long id) {
        var s = submissions.selectById(id); if (s == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); return s;
    }
    private EduSubmissionVO submissionVO(EduSubmission s, EduTask t) {
        var vo = new EduSubmissionVO(); BeanUtils.copyProperties(s, vo); var account = reads.account(s.getStudentId());
        vo.setTaskTitle(t.getTaskTitle()); vo.setTargetClass(t.getTargetClass()); vo.setRubricJson(t.getRubricJson());
        vo.setStudentName(account == null ? "" : account.realName()); vo.setStudentNumber(account == null ? "" : account.username()); return vo;
    }
    public EduSubmissionVO detailSubmission(Long id, LoginUser user) {
        var s = submission(id); var t = task(s.getTaskId(), false);
        if ("STUDENT".equals(user.roleType())) { if (!s.getStudentId().equals(user.id())) throw new BusinessException(ErrorCode.FORBIDDEN); }
        else requireOwner(t, user);
        return submissionVO(s, t);
    }
    private void validateGrade(SubmissionGradeDTO dto, EduTask t) {
        if (dto.getReviewStatus() == 2) return;
        if (dto.getScore() == null) invalid("已批改必须填写总分");
        JsonNode rubric = t.getRubricJson(), details = dto.getRubricDetailJson();
        if (rubric == null || rubric.isNull()) {
            if (details != null && !details.isNull() && (!details.isObject() || !details.isEmpty())) invalid("课题未配置分项评分细则");
            return;
        }
        if (details == null || !details.isObject() || details.size() != rubric.size()) invalid("请完整填写课题分项得分");
        BigDecimal sum = BigDecimal.ZERO;
        for (var item : rubric) {
            var value = details.path(item.path("item").asText());
            if (!value.isNumber() || value.decimalValue().signum() < 0 || value.decimalValue().compareTo(item.path("maxScore").decimalValue()) > 0) invalid("分项得分必须在对应满分范围内");
            sum = sum.add(value.decimalValue());
        }
        if (sum.compareTo(dto.getScore()) != 0) invalid("分项得分之和必须等于总分");
    }
    @Transactional(rollbackFor = Exception.class)
    public EduSubmissionVO grade(Long id, SubmissionGradeDTO dto, LoginUser user) {
        teacher(user); var initial = submission(id); var t = task(initial.getTaskId(), true); requireOwner(t, user);
        var s = submission(id); validateGrade(dto, t);
        s.setReviewStatus(dto.getReviewStatus()); s.setTeacherComment(dto.getTeacherComment()); s.setReviewedAt(LocalDateTime.now());
        s.setScore(dto.getReviewStatus() == 1 ? dto.getScore() : null); s.setRubricDetailJson(dto.getReviewStatus() == 1 ? dto.getRubricDetailJson() : null);
        submissions.updateById(s); log.info("Teacher {} reviewed submission {} status={}", user.id(), id, dto.getReviewStatus()); return submissionVO(s, t);
    }
    @Transactional(readOnly = true)
    public EduAnalyticsVO analytics(Long taskId, String targetClass, LoginUser user) {
        teacher(user); if (taskId != null) requireOwner(task(taskId, false), user);
        Long teacherId = admin(user) ? null : user.id();
        var raw = reads.summary(teacherId, taskId, targetClass);
        var summary = new Summary(raw.totalSubmissions(), raw.gradedCount(), round(raw.passRate()), round(raw.avgScore()));
        var b = reads.buckets(teacherId, taskId, targetClass);
        var buckets = List.of(new ScoreBucket("<60 (不及格)", b.failCount()), new ScoreBucket("60-69 (合格)", b.passCount()),
            new ScoreBucket("70-79 (中等)", b.mediumCount()), new ScoreBucket("80-89 (良好)", b.goodCount()), new ScoreBucket("90-100 (优秀)", b.excellentCount()));
        var days = new TreeMap<String, long[]>();
        reads.submissionDays(teacherId, taskId, targetClass).forEach(d -> days.computeIfAbsent(d.date().trim(), k -> new long[2])[0] = d.count());
        reads.debugDays(teacherId, taskId, targetClass).forEach(d -> days.computeIfAbsent(d.date().trim(), k -> new long[2])[1] = d.count());
        var trends = days.entrySet().stream().map(e -> new DailyTrend(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
        return new EduAnalyticsVO(summary, buckets, radar(taskId, targetClass, user), trends, reads.errors(teacherId, taskId, targetClass));
    }
    private double round(double value) { return Math.round(value * 100.0) / 100.0; }
    private double rate(double total, long count) { return count == 0 ? 0 : round(total / count * 100); }
    private List<RadarDimension> radar(Long taskId, String targetClass, LoginUser user) {
        double[] sum = new double[5]; long[] count = new long[5];
        for (var s : submissions.selectList(submissionScope(taskId, targetClass, user))) {
            var snapshot = s.getCanvasSnapshotJson(); List<WfNodeEdge> ns = new ArrayList<>(), es = new ArrayList<>();
            for (var n : snapshot.path("nodes")) ns.add(json.convertValue(n, WfNodeEdge.class));
            for (var e : snapshot.path("edges")) es.add(json.convertValue(e, WfNodeEdge.class));
            count[0]++;
            try { sorter.sortAndValidate(ns, es); sum[0]++; } catch (BusinessException ignored) { /* invalid topology contributes zero */ }
            for (var n : ns) {
                var cfg = n.getConfigJson() == null ? Map.<String,Object>of() : n.getConfigJson();
                if ("LLM".equals(n.getNodeType())) {
                    count[1]++;
                    if (StringUtils.hasText(Objects.toString(cfg.get("systemPrompt"), "")) && StringUtils.hasText(Objects.toString(cfg.get("userPromptTemplate"), ""))) sum[1]++;
                }
                if ("CONDITION".equals(n.getNodeType())) {
                    count[2]++; Set<String> handles = new HashSet<>();
                    es.stream().filter(e -> n.getElementKey().equals(e.getSourceKey())).forEach(e -> handles.add(e.getSourceHandle()));
                    if (handles.containsAll(Set.of("true", "false")) && StringUtils.hasText(Objects.toString(cfg.get("fieldExpr"), ""))
                        && Set.of("CONTAINS", "EQUALS", "LENGTH_GT").contains(Objects.toString(cfg.get("operator"), ""))) sum[2]++;
                }
            }
            if (s.getReviewStatus() == 1 && s.getRubricDetailJson() != null) {
                var rubric = task(s.getTaskId(), false).getRubricJson();
                if (rubric != null) for (var item : rubric) {
                    String name = item.path("item").asText();
                    if (name.contains("报告") && item.path("maxScore").asDouble() > 0 && s.getRubricDetailJson().path(name).isNumber()) {
                        sum[4] += s.getRubricDetailJson().path(name).asDouble() / item.path("maxScore").asDouble(); count[4]++;
                    }
                }
            }
        }
        var execution = reads.executions(admin(user) ? null : user.id(), taskId, targetClass);
        sum[3] = execution.succeeded(); count[3] = execution.total();
        String[] labels = {"拓扑设计规范度", "大模型Prompt质量", "分支逻辑完备性", "沙箱执行成功率", "实训报告质量"};
        List<RadarDimension> result = new ArrayList<>();
        for (int i=0; i<5; i++) result.add(new RadarDimension(labels[i], rate(sum[i], count[i])));
        return result;
    }
}
