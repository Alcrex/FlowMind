package com.flowmind.platform.workflow.vo;
import com.flowmind.platform.workflow.entity.WfNodeEdge;
import java.util.List;
public record WorkflowDetailVO(WorkflowSummaryVO workflow, List<WfNodeEdge> nodes, List<WfNodeEdge> edges) {}
