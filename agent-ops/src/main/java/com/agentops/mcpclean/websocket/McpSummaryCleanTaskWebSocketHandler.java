package com.agentops.mcpclean.websocket;

import com.agentops.mcpclean.McpSummaryCleanTaskTracker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpSummaryCleanTaskWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final McpSummaryCleanTaskTracker taskTracker;
    private final McpSummaryCleanTaskWebSocketBroadcaster broadcaster;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        broadcaster.register(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String action = root.path("action").asText("");
            String taskId = root.path("taskId").asText("");
            if ("subscribe".equalsIgnoreCase(action)) {
                broadcaster.subscribe(session, taskId);
                taskTracker.find(taskId).ifPresent(broadcaster::publish);
                return;
            }
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                        Map.of("type", "error", "message", "unsupported action"))));
            }
        } catch (Exception e) {
            log.warn("failed to handle mcp clean websocket message. sessionId={}", session.getId(), e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("mcp clean websocket transport error. sessionId={}", session.getId(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        broadcaster.unregister(session);
    }
}

