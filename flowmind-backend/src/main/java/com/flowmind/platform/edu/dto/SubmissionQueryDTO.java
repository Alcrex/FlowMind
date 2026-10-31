package com.flowmind.platform.edu.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
@Data
public class SubmissionQueryDTO {
    @Positive private Long taskId;
    @Size(max=100) private String targetClass;
    @Min(0) @Max(2) private Integer reviewStatus;
    @Min(0) @Max(1) private Integer isLate;
    @Min(0) @Max(1) private Integer autoCheckPassed;
    @DecimalMin("0") @DecimalMax("100") private BigDecimal minScore;
    @DecimalMin("0") @DecimalMax("100") private BigDecimal maxScore;
    @Size(max=64) private String studentKeyword;
    @NotNull @Min(1) private Integer pageNum = 1;
    @NotNull @Min(1) @Max(100) private Integer pageSize = 10;
}
