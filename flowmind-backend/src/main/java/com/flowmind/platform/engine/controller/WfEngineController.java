package com.flowmind.platform.engine.controller;
import com.flowmind.platform.common.api.*;
import com.flowmind.platform.common.exception.BusinessException;
import com.flowmind.platform.common.security.*;
import com.flowmind.platform.engine.dto.*;
import com.flowmind.platform.engine.entity.WfExecRecord;
import com.flowmind.platform.engine.service.WfEngineService;
import com.flowmind.platform.engine.vo.ExecRecordDetailVO;
import com.flowmind.platform.workflow.vo.WorkflowDetailVO;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
@RestController
@RequestMapping("/api/v1/engine")
@RequiredArgsConstructor
public class WfEngineController {
    private final WfEngineService service;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    @PreDestroy void close() { workers.shutdownNow(); }
    @PostMapping("/execute/sync") public Result<ExecRecordDetailVO> sync(@Valid @RequestBody WorkflowExecuteReqDTO dto) { return Result.success(service.executeSync(dto,SecurityUtils.getLoginUser())); }
    @PostMapping(value="/execute/stream", produces="text/event-stream;charset=UTF-8")
    public SseEmitter stream(@Valid @RequestBody WorkflowExecuteReqDTO dto, HttpServletRequest request, HttpServletResponse response) {
        LoginUser user = SecurityUtils.getLoginUser(); var plan = service.prepare(dto,user);
        // Preserve authentication for the ASYNC redispatch used to finish this SSE request.
        new RequestAttributeSecurityContextRepository().saveContext(SecurityContextHolder.getContext(),request,response);
        var emitter = new SseEmitter(300000L); var closed = new AtomicBoolean();
        emitter.onCompletion(() -> closed.set(true)); emitter.onTimeout(() -> { closed.set(true); emitter.complete(); }); emitter.onError(error -> closed.set(true));
        workers.submit(() -> {
            try {
                var result = service.execute(plan,user,(name,data) -> send(emitter,closed,name,Result.success(data)));
                // execute returned through the transactional proxy: final event confirms durable persistence.
                send(emitter,closed,"WORKFLOW_COMPLETE",Result.success(result));
            } catch (Exception ex) {
                ErrorCode code = ex instanceof BusinessException business ? business.getErrorCode() : ErrorCode.INTERNAL_ERROR;
                send(emitter,closed,"ERROR",Result.failure(code,ex instanceof BusinessException ? ex.getMessage() : code.getMessage()));
            } finally { if (!closed.get()) emitter.complete(); }
        });
        return emitter;
    }
    private void send(SseEmitter emitter,AtomicBoolean closed,String name,Result<?> data) {
        if (closed.get()) return;
        try { emitter.send(SseEmitter.event().name(name).data(data,MediaType.APPLICATION_JSON)); }
        catch(IOException | IllegalStateException ex) { closed.set(true); }
    }
    @PostMapping("/text-to-flow") public Result<WorkflowDetailVO> generate(@Valid @RequestBody TextToFlowReqDTO dto) { return Result.success(service.textToFlow(dto,SecurityUtils.getLoginUser())); }
    @GetMapping("/records") public Result<PageResult<WfExecRecord>> records(@Valid @ModelAttribute ExecRecordQueryDTO dto) { return Result.success(service.query(dto,SecurityUtils.getLoginUser())); }
    @GetMapping("/records/{id}") public Result<ExecRecordDetailVO> detail(@PathVariable Long id) { return Result.success(service.detail(id,SecurityUtils.getLoginUser())); }
}
