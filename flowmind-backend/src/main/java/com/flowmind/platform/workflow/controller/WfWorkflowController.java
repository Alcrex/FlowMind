package com.flowmind.platform.workflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowmind.platform.common.api.*;
import com.flowmind.platform.common.security.SecurityUtils;
import com.flowmind.platform.workflow.dto.*;
import com.flowmind.platform.workflow.service.WfWorkflowService;
import com.flowmind.platform.workflow.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WfWorkflowController {
    private final WfWorkflowService service;
    public record RollbackDTO(@NotBlank @Size(max=32) String versionTag) {}
    public record CollaboratorsDTO(@NotNull @Size(max=50) List<@NotNull @Positive Long> collaboratorIds) {}
    @GetMapping("/dashboard") public Result<WorkspaceDashboardVO> dashboard() { return Result.success(service.dashboard(SecurityUtils.getLoginUser())); }
    @GetMapping public Result<PageResult<WorkflowSummaryVO>> query(@Valid WorkflowQueryDTO dto) { return Result.success(service.query(dto, SecurityUtils.getLoginUser())); }
    @PostMapping public Result<WorkflowDetailVO> create(@Valid @RequestBody WorkflowCreateDTO dto) { return Result.success(service.create(dto, SecurityUtils.getLoginUser())); }
    @GetMapping("/{id}") public Result<WorkflowDetailVO> detail(@PathVariable Long id) { return Result.success(service.detail(id, SecurityUtils.getLoginUser())); }
    @PutMapping("/{id}/canvas") public Result<WorkflowDetailVO> save(@PathVariable Long id, @Valid @RequestBody CanvasSaveDTO dto) { return Result.success(service.saveCanvas(id, dto, SecurityUtils.getLoginUser())); }
    @GetMapping("/{id}/versions") public Result<JsonNode> versions(@PathVariable Long id) { return Result.success(service.versions(id, SecurityUtils.getLoginUser())); }
    @PostMapping("/{id}/versions/rollback") public Result<WorkflowDetailVO> rollback(@PathVariable Long id, @Valid @RequestBody RollbackDTO dto) { return Result.success(service.rollback(id, dto.versionTag(), SecurityUtils.getLoginUser())); }
    @PutMapping("/{id}/collaborators") public Result<Boolean> collaborators(@PathVariable Long id, @Valid @RequestBody CollaboratorsDTO dto) { return Result.success(service.collaborators(id, dto.collaboratorIds(), SecurityUtils.getLoginUser())); }
    @DeleteMapping("/{id}") public Result<Boolean> delete(@PathVariable Long id) { return Result.success(service.delete(id, SecurityUtils.getLoginUser())); }
    @PostMapping("/{id}/clone") public Result<WorkflowDetailVO> cloneWorkflow(@PathVariable Long id) { return Result.success(service.cloneWorkflow(id, SecurityUtils.getLoginUser())); }
}
