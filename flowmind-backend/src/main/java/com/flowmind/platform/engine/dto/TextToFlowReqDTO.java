package com.flowmind.platform.engine.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class TextToFlowReqDTO {
    @NotBlank @Size(max=1000) private String prompt;
    @NotBlank @Pattern(regexp="CODE_ASSIST|DATA_ANALYSIS|TEXT_GEN|KNOWLEDGE_QA|OTHER") private String category = "CODE_ASSIST";
}
