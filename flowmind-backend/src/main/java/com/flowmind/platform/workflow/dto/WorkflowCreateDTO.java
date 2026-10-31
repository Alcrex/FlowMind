package com.flowmind.platform.workflow.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class WorkflowCreateDTO {
    @NotBlank @Size(max=128) private String wfName;
    @Size(max=1000) private String description;
    @NotBlank @Pattern(regexp="CODE_ASSIST|DATA_ANALYSIS|TEXT_GEN|KNOWLEDGE_QA|OTHER") private String category = "CODE_ASSIST";
    @Size(max=255) private String tags;
    @NotNull @Min(0) @Max(1) private Integer isPublic = 0;
}
