package com.flowmind.platform.system.controller;
import com.flowmind.platform.common.api.*;
import com.flowmind.platform.common.security.SecurityUtils;
import com.flowmind.platform.system.dto.*;
import com.flowmind.platform.system.entity.SysModelAuditLog;
import com.flowmind.platform.system.service.*;
import com.flowmind.platform.system.vo.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminSystemController {
    private final SysUserService users;
    private final SysAuditAndConfigService audit;
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users") public Result<PageResult<SysUserVO>> users(@Valid @ModelAttribute UserQueryDTO q) { return Result.success(users.query(q)); }
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/users/{id}") public Result<SysUserVO> update(@PathVariable @Positive Long id, @Valid @RequestBody UserUpdateDTO input, HttpServletRequest req) { return Result.success(users.update(id, input, req)); }
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/audit-logs") public Result<PageResult<SysModelAuditLog>> logs(@Valid @ModelAttribute AuditLogQueryDTO q) { return Result.success(audit.query(q)); }
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/models/config") public Result<ModelConfigDTO> config() { return Result.success(audit.getConfig()); }
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/models/config") public Result<ModelConfigDTO> config(@Valid @RequestBody ModelConfigDTO input, HttpServletRequest req) { return Result.success(audit.updateConfig(input, SecurityUtils.getLoginUser(), req)); }
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
    @GetMapping("/dashboard/big-screen") public Result<BigScreenStatsVO> bigScreen() { return Result.success(audit.bigScreen()); }
}
