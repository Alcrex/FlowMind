package com.flowmind.platform.edu.dto;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class SubmissionGradeDTO {
    private JsonNode rubricDetailJson;
    @DecimalMin("0") @DecimalMax("100") @Digits(integer=3, fraction=2) private BigDecimal score;
    @NotBlank @Size(max=20000) private String teacherComment;
    @NotNull @Min(1) @Max(2) private Integer reviewStatus = 1;
}
