package com.agentengine.skill.embedding.config;

import com.agentengine.skill.embedding.websocket.EmbeddingTaskWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class EmbeddingWebSocketConfig implements WebSocketConfigurer {

    private final EmbeddingTaskWebSocketHandler embeddingTaskWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(embeddingTaskWebSocketHandler, "/ws/embedding-task")
                .setAllowedOriginPatterns("*");
    }
}
