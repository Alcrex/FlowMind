package com.flowmind.platform.system.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
@Data
public class AuditLogQueryDTO {
    @Size(max = 64) private String username;
    @Pattern(regexp = "AUTH|WORKFLOW|ENGINE|EDU|ADMIN") private String moduleName;
    @Size(max = 50) private String actionType;
    @Pattern(regexp = "DEEPSEEK|QWEN|MOCK_LOCAL") private String modelProvider;
    @Min(100) @Max(599) private Integer httpStatus;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime startTime;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") private LocalDateTime endTime;
    @Min(1) private int pageNum = 1;
    @Min(1) @Max(100) private int pageSize = 10;
    @AssertTrue(message = "时间区间无效") public boolean isTimeRangeValid() { return startTime == null || endTime == null || !startTime.isAfter(endTime); }
}
