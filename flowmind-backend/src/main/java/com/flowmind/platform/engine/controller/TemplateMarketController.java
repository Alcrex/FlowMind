package com.flowmind.platform.engine.controller;
import com.flowmind.platform.common.api.*;
import com.flowmind.platform.common.security.SecurityUtils;
import com.flowmind.platform.engine.dto.TemplateMarketQueryDTO;
import com.flowmind.platform.engine.service.TemplateMarketService;
import com.flowmind.platform.workflow.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/v1/market/templates")
@RequiredArgsConstructor
public class TemplateMarketController {
    private final TemplateMarketService service;
    @GetMapping public Result<PageResult<WorkflowSummaryVO>> query(@Valid @ModelAttribute TemplateMarketQueryDTO dto) { return Result.success(service.query(dto)); }
    @GetMapping("/{id}") public Result<WorkflowDetailVO> detail(@PathVariable Long id) { return Result.success(service.detail(id)); }
    @PostMapping("/{id}/like") public Result<Boolean> like(@PathVariable Long id) { SecurityUtils.getLoginUser(); return Result.success(service.like(id)); }
    @PostMapping("/{id}/fork") public Result<Map<String,Long>> fork(@PathVariable Long id) { return new Result<>(200,"成功克隆至个人工作流空间",Map.of("newWorkflowId",service.fork(id,SecurityUtils.getLoginUser())),System.currentTimeMillis()); }
}
