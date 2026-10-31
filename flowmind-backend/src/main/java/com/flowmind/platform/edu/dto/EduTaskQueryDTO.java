package com.flowmind.platform.edu.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class EduTaskQueryDTO {
    @Size(max=128) private String keyword;
    @Size(max=100) private String courseName;
    @Size(max=100) private String targetClass;
    @Min(0) @Max(2) private Integer taskStatus;
    @NotNull @Min(1) private Integer pageNum = 1;
    @NotNull @Min(1) @Max(100) private Integer pageSize = 10;
}
