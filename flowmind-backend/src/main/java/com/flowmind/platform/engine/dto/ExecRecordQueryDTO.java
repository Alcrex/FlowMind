package com.flowmind.platform.engine.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
@Data
public class ExecRecordQueryDTO {
    @Positive private Long workflowId;
    @Pattern(regexp="RUNNING|SUCCESS|FAILED") private String execStatus;
    @Pattern(regexp="SANDBOX|TEACHER_REVIEW|TEMPLATE_TEST") private String triggerMode;
    @Min(0) private Integer minDuration;
    @Min(0) private Integer maxDuration;
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss") private LocalDateTime startTime;
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss") private LocalDateTime endTime;
    @NotNull @Min(1) private Integer pageNum = 1;
    @NotNull @Min(1) @Max(100) private Integer pageSize = 10;
    @AssertTrue(message="查询区间上下限无效") public boolean isRangeValid() {
        return (minDuration == null || maxDuration == null || minDuration <= maxDuration)
            && (startTime == null || endTime == null || !startTime.isAfter(endTime));
    }
}
