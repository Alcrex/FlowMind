package com.flowmind.platform.workflow.mapper;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper
public interface WorkflowReadMapper {
    @Select("SELECT real_name FROM sys_user WHERE id = #{id}") String userName(Long id);
    @Select("SELECT COUNT(*) FROM sys_user WHERE id = #{id} AND status = 1") int activeUser(Long id);
    @Select("SELECT token_quota FROM sys_user WHERE id = #{id}") Long quota(Long id);
    @Select("SELECT COUNT(*) FROM edu_task t JOIN sys_user u ON u.dept_class = t.target_class WHERE u.id = #{id} AND t.task_status = 1 AND t.deadline >= CURRENT_TIMESTAMP AND NOT EXISTS (SELECT 1 FROM edu_submission s WHERE s.task_id = t.id AND s.student_id = u.id AND s.review_status <> 2)")
    long pendingTasks(Long id);
    @Select("SELECT COUNT(*) FROM edu_submission s JOIN edu_task t ON t.id = s.task_id WHERE s.workflow_id = #{workflowId} AND t.teacher_id = #{userId}")
    int canReview(@Param("workflowId") Long workflowId, @Param("userId") Long userId);
}
