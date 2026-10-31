package com.flowmind.platform.edu.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "edu_submission", autoResultMap = true)
public class EduSubmission {
    @TableId(type = IdType.AUTO) private Long id;
    private Long taskId;
    private Long studentId;
    private Long workflowId;
    private String versionTag;
    @TableField(typeHandler = JacksonTypeHandler.class) private JsonNode canvasSnapshotJson;
    private String studentReport;
    private Integer isLate;
    private Integer autoCheckPassed;
    @TableField(typeHandler = JacksonTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private JsonNode rubricDetailJson;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private BigDecimal score;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private String teacherComment;
    private Integer reviewStatus;
    private LocalDateTime submittedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS) private LocalDateTime reviewedAt;
}
