package com.flowmind.platform.workflow.vo;
import java.util.List;
public record WorkspaceDashboardVO(long myWorkflowCount, long collabWorkflowCount, long pendingTaskCount,
        long remainingTokenQuota, List<WorkflowSummaryVO> recentWorkflows) {}
