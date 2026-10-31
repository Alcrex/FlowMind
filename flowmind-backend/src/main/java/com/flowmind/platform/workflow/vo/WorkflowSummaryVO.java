package com.flowmind.platform.workflow.vo;
import com.flowmind.platform.workflow.entity.WfWorkflow;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;
@EqualsAndHashCode(callSuper=true)
@Data
public class WorkflowSummaryVO extends WfWorkflow {
    private String creatorName;
    @Override @JsonIgnore public JsonNode getHistorySnapshotsJson() { return super.getHistorySnapshotsJson(); }
}
