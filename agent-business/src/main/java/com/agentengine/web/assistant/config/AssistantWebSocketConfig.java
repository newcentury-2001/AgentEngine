package com.agentengine.web.assistant.config;

import com.agentengine.web.assistant.websocket.AssistantTaskWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class AssistantWebSocketConfig implements WebSocketConfigurer {

    private final AssistantTaskWebSocketHandler assistantTaskWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(assistantTaskWebSocketHandler, "/ws/assistant-task")
                .setAllowedOriginPatterns("*");
    }
}
