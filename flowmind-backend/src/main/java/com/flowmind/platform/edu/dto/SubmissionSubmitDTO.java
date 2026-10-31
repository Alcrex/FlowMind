package com.flowmind.platform.edu.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class SubmissionSubmitDTO {
    @NotNull @Positive private Long taskId;
    @NotNull @Positive private Long workflowId;
    @NotBlank @Size(max=20000) private String studentReport;
}
