package com.flowmind.platform.engine.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class TemplateMarketQueryDTO {
    @Size(max=128) private String keyword;
    @Pattern(regexp="CODE_ASSIST|DATA_ANALYSIS|TEXT_GEN|KNOWLEDGE_QA|OTHER") private String category;
    @Size(max=64) private String tag;
    @Min(0) private Integer minNodeCount;
    @Min(0) private Integer maxNodeCount;
    @NotBlank @Pattern(regexp="popular|likes|latest") private String sortBy = "popular";
    @NotNull @Min(1) private Integer pageNum = 1;
    @NotNull @Min(1) @Max(100) private Integer pageSize = 12;
    @AssertTrue(message="节点数量区间无效") public boolean isRangeValid() {
        return minNodeCount == null || maxNodeCount == null || minNodeCount <= maxNodeCount;
    }
}
