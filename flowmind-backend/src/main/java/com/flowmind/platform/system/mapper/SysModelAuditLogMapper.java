package com.flowmind.platform.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flowmind.platform.system.entity.SysModelAuditLog;
import com.flowmind.platform.system.vo.BigScreenStatsVO.*;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SysModelAuditLogMapper extends BaseMapper<SysModelAuditLog> {
    @Select("SELECT (SELECT COUNT(*) FROM sys_user) AS totalUsers, (SELECT COUNT(*) FROM wf_workflow WHERE status = 1) AS totalWorkflows, (SELECT COUNT(*) FROM wf_exec_record) AS totalExecutions, (SELECT COALESCE(SUM(token_used),0) FROM sys_user) AS totalTokensUsed")
    Kpi kpi();

    @Select("SELECT COALESCE(u.dept_class, '未分班') AS deptClass, u.role_type AS roleType, COALESCE(a.model_provider, 'MOCK_LOCAL') AS modelProvider, SUM(a.cost_tokens) AS tokens FROM sys_model_audit_log a JOIN sys_user u ON a.user_id = u.id WHERE a.action_type = 'EXEC_AI' AND a.cost_tokens > 0 GROUP BY u.dept_class, u.role_type, a.model_provider")
    List<TokenFlow> tokenFlows();

    // Independent correlated counts avoid multiplying workflow and execution rows in a join.
    @Select("SELECT d.dept_class AS deptClass, (SELECT COUNT(*) FROM wf_workflow w JOIN sys_user u ON w.creator_id = u.id WHERE w.status = 1 AND COALESCE(u.dept_class, '未分班') = d.dept_class) AS workflowCount, (SELECT COUNT(*) FROM wf_exec_record e JOIN sys_user u ON e.trigger_user_id = u.id WHERE COALESCE(u.dept_class, '未分班') = d.dept_class) AS execCount FROM (SELECT DISTINCT COALESCE(dept_class, '未分班') AS dept_class FROM sys_user) d ORDER BY execCount DESC, workflowCount DESC, deptClass")
    List<DeptRanking> deptRanking();

    @Select("SELECT created_at AS createdAt, duration_ms AS durationMs FROM sys_model_audit_log WHERE action_type = 'EXEC_AI' AND created_at >= #{start} AND created_at < #{end}")
    List<CallSample> hourlySamples(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
