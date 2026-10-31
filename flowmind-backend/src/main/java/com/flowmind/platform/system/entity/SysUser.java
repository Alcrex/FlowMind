package com.flowmind.platform.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO) private Long id;
    private String username;
    @JsonIgnore private String passwordHash;
    private String realName;
    private String roleType;
    private String deptClass;
    private String email;
    private String avatarUrl;
    private Integer tokenQuota;
    private Integer tokenUsed;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
