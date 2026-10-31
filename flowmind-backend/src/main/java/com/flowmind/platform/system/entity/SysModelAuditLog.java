package com.flowmind.platform.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName(value = "sys_model_audit_log", autoResultMap = true)
public class SysModelAuditLog {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String username;
    private String moduleName;
    private String actionType;
    private String modelProvider;
    private String requestUri;
    private String requestMethod;
    private String requestIp;
    private Integer httpStatus;
    private Integer costTokens;
    private Integer durationMs;
    @TableField(typeHandler = JacksonTypeHandler.class) private JsonNode detailJson;
    private String errorMsg;
    private LocalDateTime createdAt;
}
