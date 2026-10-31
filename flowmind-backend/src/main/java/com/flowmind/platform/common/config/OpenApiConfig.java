package com.flowmind.platform.common.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
    @Bean public OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("FlowMind API").version("v1"))
            .components(new Components().addSecuritySchemes("Bearer", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList("Bearer"));
    }
}
