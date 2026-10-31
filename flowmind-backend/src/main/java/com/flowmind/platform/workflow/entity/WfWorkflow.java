package com.flowmind.platform.workflow.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.*;
@Data
@TableName(value = "wf_workflow", autoResultMap = true)
public class WfWorkflow {
    @TableId(type = IdType.AUTO) private Long id;
    private String wfCode;
    private String wfName;
    private String description;
    private String category;
    private String tags;
    private Long creatorId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> collaboratorIds;
    private Integer isPublic;
    private Integer nodeCount;
    private Integer edgeCount;
    private Integer forkCount;
    private Integer likeCount;
    private String versionTag;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode historySnapshotsJson;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
