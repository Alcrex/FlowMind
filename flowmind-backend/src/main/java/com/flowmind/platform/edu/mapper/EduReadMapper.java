package com.flowmind.platform.edu.mapper;

import com.flowmind.platform.edu.vo.EduAnalyticsVO.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** Cross-module reads stay inside edu; EXISTS avoids counting a shared workflow once per student. */
@Mapper
public interface EduReadMapper {
    record Account(Long id, String username, String realName, String deptClass) {}
    record ExecutionStats(long total, long succeeded) {}
    record DayCount(String date, long count) {}
    record BucketCounts(long failCount, long passCount, long mediumCount, long goodCount, long excellentCount) {}

    @Select("SELECT id, username, real_name, dept_class FROM sys_user WHERE id=#{id}")
    Account account(Long id);

    String SCOPE = "<if test='teacherId != null'> AND t.teacher_id=#{teacherId}</if>"
        + "<if test='taskId != null'> AND t.id=#{taskId}</if>"
        + "<if test='targetClass != null and targetClass != &quot;&quot;'> AND t.target_class=#{targetClass}</if>";
    String SUBMISSIONS = " FROM edu_submission s JOIN edu_task t ON t.id=s.task_id WHERE 1=1 " + SCOPE;
    String EXECUTIONS = " FROM wf_exec_record r WHERE r.trigger_mode='SANDBOX' AND EXISTS "
        + "(SELECT 1 FROM edu_submission s JOIN edu_task t ON t.id=s.task_id "
        + "WHERE s.workflow_id=r.workflow_id AND s.student_id=r.trigger_user_id " + SCOPE + ")";

    @Select("<script>SELECT COUNT(*) AS totalSubmissions, "
        + "COALESCE(SUM(CASE WHEN s.review_status=1 AND s.score IS NOT NULL THEN 1 ELSE 0 END),0) AS gradedCount, "
        + "COALESCE(100.0*SUM(CASE WHEN s.review_status=1 AND s.score &gt;=60 THEN 1 ELSE 0 END) / "
        + "NULLIF(SUM(CASE WHEN s.review_status=1 AND s.score IS NOT NULL THEN 1 ELSE 0 END),0),0) AS passRate, "
        + "COALESCE(AVG(CASE WHEN s.review_status=1 THEN s.score ELSE NULL END),0) AS avgScore " + SUBMISSIONS + "</script>")
    Summary summary(@Param("teacherId") Long teacherId, @Param("taskId") Long taskId, @Param("targetClass") String targetClass);

    @Select("<script>SELECT "
        + "COALESCE(SUM(CASE WHEN s.score &lt;60 THEN 1 ELSE 0 END),0), "
        + "COALESCE(SUM(CASE WHEN s.score &gt;=60 AND s.score &lt;70 THEN 1 ELSE 0 END),0), "
        + "COALESCE(SUM(CASE WHEN s.score &gt;=70 AND s.score &lt;80 THEN 1 ELSE 0 END),0), "
        + "COALESCE(SUM(CASE WHEN s.score &gt;=80 AND s.score &lt;90 THEN 1 ELSE 0 END),0), "
        + "COALESCE(SUM(CASE WHEN s.score &gt;=90 THEN 1 ELSE 0 END),0) "
        + SUBMISSIONS + " AND s.review_status=1</script>")
    BucketCounts buckets(@Param("teacherId") Long teacherId, @Param("taskId") Long taskId, @Param("targetClass") String targetClass);

    @Select("<script>SELECT CAST(CAST(s.submitted_at AS DATE) AS CHAR(10)) AS submission_date, COUNT(*) AS n "
        + SUBMISSIONS + " GROUP BY CAST(CAST(s.submitted_at AS DATE) AS CHAR(10)) ORDER BY submission_date</script>")
    List<DayCount> submissionDays(@Param("teacherId") Long teacherId, @Param("taskId") Long taskId, @Param("targetClass") String targetClass);

    @Select("<script>SELECT CAST(CAST(r.started_at AS DATE) AS CHAR(10)) AS execution_date, COUNT(*) AS n "
        + EXECUTIONS + " GROUP BY CAST(CAST(r.started_at AS DATE) AS CHAR(10)) ORDER BY execution_date</script>")
    List<DayCount> debugDays(@Param("teacherId") Long teacherId, @Param("taskId") Long taskId, @Param("targetClass") String targetClass);

    @Select("<script>SELECT COUNT(*), COALESCE(SUM(CASE WHEN r.exec_status='SUCCESS' THEN 1 ELSE 0 END),0) "
        + EXECUTIONS + " AND r.exec_status IN ('SUCCESS','FAILED')</script>")
    ExecutionStats executions(@Param("teacherId") Long teacherId, @Param("taskId") Long taskId, @Param("targetClass") String targetClass);

    @Select("<script>SELECT l.node_type, COUNT(*) AS n FROM wf_exec_node_log l JOIN wf_exec_record r ON r.id=l.exec_record_id "
        + "WHERE l.step_status='ERROR' AND r.trigger_mode='SANDBOX' AND EXISTS "
        + "(SELECT 1 FROM edu_submission s JOIN edu_task t ON t.id=s.task_id "
        + "WHERE s.workflow_id=r.workflow_id AND s.student_id=r.trigger_user_id " + SCOPE + ") "
        + "GROUP BY l.node_type ORDER BY COUNT(*) DESC, l.node_type</script>")
    List<ErrorNode> errors(@Param("teacherId") Long teacherId, @Param("taskId") Long taskId, @Param("targetClass") String targetClass);
}
