package com.agentengine.web.assistant.websocket;

import com.agentengine.web.assistant.service.AssistantStateMachineService;
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
public class AssistantTaskWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final AssistantStateMachineService stateMachineService;
    private final AssistantTaskWebSocketBroadcaster broadcaster;

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
                stateMachineService.findByTaskId(taskId)
                        .ifPresent(s -> broadcaster.publish("TASK_STATE_CHANGED", s, "subscribed"));
                return;
            }
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                        Map.of("type", "error", "message", "unsupported action"))));
            }
        } catch (Exception e) {
            log.warn("failed to handle assistant websocket message. sessionId={}", session.getId(), e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("assistant websocket transport error. sessionId={}", session.getId(), exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        broadcaster.unregister(session);
    }
}
