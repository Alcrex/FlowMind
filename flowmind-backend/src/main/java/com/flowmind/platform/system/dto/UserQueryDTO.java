package com.flowmind.platform.system.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class UserQueryDTO {
    @Size(max = 64) private String keyword;
    @Pattern(regexp = "ADMIN|TEACHER|STUDENT") private String roleType;
    @Size(max = 100) private String deptClass;
    @Min(0) @Max(1) private Integer status;
    @Min(0) private Integer minQuota;
    @Min(0) private Integer maxQuota;
    @Min(1) private int pageNum = 1;
    @Min(1) @Max(100) private int pageSize = 10;
    @AssertTrue(message = "算力区间无效") public boolean isQuotaRangeValid() { return minQuota == null || maxQuota == null || minQuota <= maxQuota; }
}
