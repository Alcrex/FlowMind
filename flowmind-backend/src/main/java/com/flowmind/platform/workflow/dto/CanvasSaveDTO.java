package com.flowmind.platform.workflow.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.*;
@Data
public class CanvasSaveDTO {
    @NotBlank @Size(max=128) private String wfName;
    @Size(max=1000) private String description;
    @NotNull @Min(0) @Max(1) private Integer isPublic;
    private boolean createSnapshot;
    @Size(max=32) private String newVersionTag;
    @NotNull @Size(max=500) @Valid private List<NodeDTO> nodes;
    @NotNull @Size(max=2000) @Valid private List<EdgeDTO> edges;
    @Data public static class NodeDTO {
        @NotBlank @Size(max=64) private String elementKey;
        @NotBlank @Pattern(regexp="INPUT|LLM|KNOWLEDGE|CODE|CONDITION|OUTPUT") private String nodeType;
        @NotBlank @Size(max=128) private String labelName;
        @NotNull private Map<String, Double> positionJson;
        @NotNull private Map<String, Object> configJson = new HashMap<>();
    }
    @Data public static class EdgeDTO {
        @NotBlank @Size(max=64) private String elementKey;
        @Size(max=128) private String labelName;
        @NotBlank @Size(max=64) private String sourceKey;
        @NotBlank @Size(max=64) private String targetKey;
        @Pattern(regexp="default|true|false") private String sourceHandle = "default";
    }
}
