package com.flowmind.platform.edu.controller;

import com.flowmind.platform.common.api.*;
import com.flowmind.platform.common.security.SecurityUtils;
import com.flowmind.platform.edu.dto.*;
import com.flowmind.platform.edu.service.EduTrainingService;
import com.flowmind.platform.edu.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/edu")
@RequiredArgsConstructor
public class EduTrainingController {
    private final EduTrainingService service;
    @GetMapping("/tasks") public Result<PageResult<EduTaskVO>> tasks(@Valid EduTaskQueryDTO dto) {
        return Result.success(service.queryTasks(dto, SecurityUtils.getLoginUser()));
    }
    @GetMapping("/tasks/{id}") public Result<EduTaskVO> task(@PathVariable @Positive Long id) {
        return Result.success(service.detailTask(id, SecurityUtils.getLoginUser()));
    }
    @PostMapping("/tasks") @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<EduTaskVO> create(@Valid @RequestBody EduTaskSaveDTO dto) { return Result.success(service.saveTask(null, dto, SecurityUtils.getLoginUser())); }
    @PutMapping("/tasks/{id}") @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<EduTaskVO> update(@PathVariable @Positive Long id, @Valid @RequestBody EduTaskSaveDTO dto) { return Result.success(service.saveTask(id, dto, SecurityUtils.getLoginUser())); }
    @DeleteMapping("/tasks/{id}") @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<Boolean> delete(@PathVariable @Positive Long id) { return Result.success(service.deleteTask(id, SecurityUtils.getLoginUser())); }
    @PostMapping("/submissions") @PreAuthorize("hasRole('STUDENT')")
    public Result<EduSubmissionVO> submit(@Valid @RequestBody SubmissionSubmitDTO dto) { return Result.success(service.submit(dto, SecurityUtils.getLoginUser())); }
    @GetMapping("/submissions") public Result<PageResult<EduSubmissionVO>> submissions(@Valid SubmissionQueryDTO dto) { return Result.success(service.querySubmissions(dto, SecurityUtils.getLoginUser())); }
    @GetMapping("/submissions/{id}") public Result<EduSubmissionVO> submission(@PathVariable @Positive Long id) { return Result.success(service.detailSubmission(id, SecurityUtils.getLoginUser())); }
    @PutMapping("/submissions/{id}/grade") @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<EduSubmissionVO> grade(@PathVariable @Positive Long id, @Valid @RequestBody SubmissionGradeDTO dto) { return Result.success(service.grade(id, dto, SecurityUtils.getLoginUser())); }
    @GetMapping("/analytics") @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Result<EduAnalyticsVO> analytics(@RequestParam(required=false) @Positive Long taskId, @RequestParam(required=false) @Size(max=100) String targetClass) {
        return Result.success(service.analytics(taskId, targetClass, SecurityUtils.getLoginUser()));
    }
}
