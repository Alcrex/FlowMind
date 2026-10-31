package com.flowmind.platform.edu.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "edu_task", autoResultMap = true)
public class EduTask {
    @TableId(type = IdType.AUTO) private Long id;
    private String taskCode;
    private String taskTitle;
    private String courseName;
    private Long teacherId;
    private String targetClass;
    private String taskRequirement;
    @TableField(typeHandler = JacksonTypeHandler.class) private JsonNode requiredNodesJson;
    @TableField(typeHandler = JacksonTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS) private JsonNode rubricJson;
    private LocalDateTime deadline;
    private Integer taskStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
