package com.flowmind.platform.engine.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;
@Data
@TableName(value = "wf_exec_record", autoResultMap = true)
public class WfExecRecord {
    @TableId(type = IdType.AUTO) private Long id;
    private String execNo;
    private Long workflowId;
    private Long triggerUserId;
    private String triggerMode;
    private String execStatus;
    private Integer totalTokens;
    private Integer durationMs;
    @TableField(typeHandler = JacksonTypeHandler.class) private Map<String, Object> inputPayload;
    @TableField(typeHandler = JacksonTypeHandler.class) private Map<String, Object> finalOutput;
    private String errorSummary;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @TableField(exist = false) private String wfName;
}
