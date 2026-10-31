package com.flowmind.platform.system.dto;
import jakarta.validation.constraints.*;
public record UserUpdateDTO(
    @Pattern(regexp = "ADMIN|TEACHER|STUDENT") String roleType,
    @Size(max = 100) String deptClass,
    @Min(0) @Max(1) Integer status,
    @Min(0) Integer tokenQuota,
    Boolean resetPassword) {}
