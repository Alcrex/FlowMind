package com.flowmind.platform.workflow.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.*;
@Data
@TableName(value = "wf_node_edge", autoResultMap = true)
public class WfNodeEdge {
    @TableId(type = IdType.AUTO) private Long id;
    private Long workflowId;
    private String elementKey;
    private String elementType;
    private String nodeType;
    private String labelName;
    private String sourceKey;
    private String targetKey;
    private String sourceHandle;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Double> positionJson;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> configJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
