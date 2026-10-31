package com.flowmind.platform.edu.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class EduTaskSaveDTO {
    @NotBlank @Size(max=128) private String taskTitle;
    @NotBlank @Size(max=100) private String courseName;
    @NotBlank @Size(max=100) private String targetClass;
    @NotBlank @Size(max=20000) private String taskRequirement;
    @NotNull private JsonNode requiredNodesJson;
    private JsonNode rubricJson;
    @NotNull @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") private LocalDateTime deadline;
    @NotNull @Min(0) @Max(2) private Integer taskStatus = 1;
}
