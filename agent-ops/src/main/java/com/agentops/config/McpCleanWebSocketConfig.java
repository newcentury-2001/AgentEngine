package com.agentops.config;

import com.agentops.mcpclean.websocket.McpSummaryCleanTaskWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@RequiredArgsConstructor
public class McpCleanWebSocketConfig implements WebSocketConfigurer {

    private final McpSummaryCleanTaskWebSocketHandler mcpSummaryCleanTaskWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mcpSummaryCleanTaskWebSocketHandler, "/ws/mcp-clean-task")
                .setAllowedOriginPatterns("*");
    }
}

