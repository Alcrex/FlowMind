package com.flowmind.platform.workflow.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
@Data
public class WorkflowQueryDTO {
    @Size(max=128) private String keyword;
    @Pattern(regexp="CODE_ASSIST|DATA_ANALYSIS|TEXT_GEN|KNOWLEDGE_QA|OTHER") private String category;
    @Min(0) @Max(1) private Integer isPublic;
    @Min(0) private Integer minNodeCount;
    @Min(0) private Integer maxNodeCount;
    @NotNull @Pattern(regexp="ALL|MINE|COLLAB") private String scope = "ALL";
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss") private LocalDateTime startTime;
    @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss") private LocalDateTime endTime;
    @Min(1) private int pageNum = 1;
    @Min(1) @Max(100) private int pageSize = 10;
    @AssertTrue(message="查询区间顺序错误") public boolean isRangeValid() {
        return (minNodeCount == null || maxNodeCount == null || minNodeCount <= maxNodeCount)
            && (startTime == null || endTime == null || !startTime.isAfter(endTime));
    }
}
