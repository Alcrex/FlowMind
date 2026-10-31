package com.flowmind.platform.system.dto;
import jakarta.validation.constraints.*;
public record RegisterReqDTO(
    @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]{4,32}$") String username,
    @NotBlank @Size(min = 6, max = 32) String password,
    @NotBlank @Size(min = 2, max = 32) String realName,
    @NotBlank @Pattern(regexp = "STUDENT|TEACHER") String roleType,
    @NotBlank @Size(max = 100) String deptClass,
    @Email @Size(max = 128) String email) {}
