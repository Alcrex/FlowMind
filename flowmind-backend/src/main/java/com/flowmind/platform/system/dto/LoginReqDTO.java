package com.flowmind.platform.system.dto;
import jakarta.validation.constraints.*;
public record LoginReqDTO(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 32) String password) {}
