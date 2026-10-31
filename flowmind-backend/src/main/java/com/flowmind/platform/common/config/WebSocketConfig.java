package com.flowmind.platform.common.config;

import com.flowmind.platform.workflow.websocket.CollabWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final CollabWebSocketHandler handler;
    private final String[] origins;
    public WebSocketConfig(CollabWebSocketHandler handler, @Value("${flowmind.allowed-origins}") String origins) {
        this.handler = handler; this.origins = origins.split(",");
    }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/collab/{workflowId}").addInterceptors(handler).setAllowedOrigins(origins);
    }
}
