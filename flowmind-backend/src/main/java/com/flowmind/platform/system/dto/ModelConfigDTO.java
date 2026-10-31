package com.flowmind.platform.system.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
@Data
public class ModelConfigDTO {
    @NotBlank @Pattern(regexp = "DEEPSEEK|QWEN|MOCK_LOCAL") private String activeProvider = "MOCK_LOCAL";
    @NotBlank @Size(max = 255) @Pattern(regexp = "https?://[^\\s?#@]+") private String baseUrl = "https://api.deepseek.com/v1";
    private String apiKeyMasked = "";
    @Size(max = 512) private String apiKeyPlain = "";
    @NotBlank @Size(max = 128) private String modelName = "deepseek-chat";
    @NotNull private Boolean enableMockFallback = true;
    @NotNull @Size(max = 200) private List<@NotBlank @Size(max = 100) String> sensitiveWords = List.of("drop table", "delete from sys_user", "rm -rf", "违法违禁");
}
