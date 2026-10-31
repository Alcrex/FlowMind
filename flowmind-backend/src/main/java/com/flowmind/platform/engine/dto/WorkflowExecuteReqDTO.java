package com.flowmind.platform.engine.dto;
import com.flowmind.platform.workflow.dto.CanvasSaveDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.*;
@Data
public class WorkflowExecuteReqDTO {
    @NotNull @Positive private Long workflowId;
    @NotBlank @Pattern(regexp="SANDBOX|TEACHER_REVIEW|TEMPLATE_TEST") private String triggerMode = "SANDBOX";
    @NotNull private Map<String, Object> inputPayload = new LinkedHashMap<>();
    @Valid private CanvasSnapshot canvasOverride;
    @Data public static class CanvasSnapshot {
        @NotNull @Size(min=2, max=500) @Valid private List<CanvasSaveDTO.NodeDTO> nodes;
        @NotNull @Size(max=2000) @Valid private List<CanvasSaveDTO.EdgeDTO> edges;
    }
}
