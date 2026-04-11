package com.agentops.mcpclean.websocket;

import com.agentops.mcpclean.model.McpSummaryCleanTaskStatusView;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpSummaryCleanTaskWebSocketBroadcaster {

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> sessionTaskIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> taskSubscribers = new ConcurrentHashMap<>();

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    public void unregister(WebSocketSession session) {
        sessions.remove(session.getId());
        Set<String> taskIds = sessionTaskIds.remove(session.getId());
        if (taskIds == null || taskIds.isEmpty()) {
            return;
        }
        for (String taskId : taskIds) {
            Set<String> subs = taskSubscribers.get(taskId);
            if (subs == null) {
                continue;
            }
            subs.remove(session.getId());
            if (subs.isEmpty()) {
                taskSubscribers.remove(taskId);
            }
        }
    }

    public void subscribe(WebSocketSession session, String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        String sid = session.getId();
        sessionTaskIds.computeIfAbsent(sid, k -> ConcurrentHashMap.newKeySet()).add(taskId);
        taskSubscribers.computeIfAbsent(taskId, k -> ConcurrentHashMap.newKeySet()).add(sid);
    }

    public void publish(McpSummaryCleanTaskStatusView status) {
        if (status == null || status.getTaskId() == null || status.getTaskId().isBlank()) {
            return;
        }
        Set<String> subs = taskSubscribers.get(status.getTaskId());
        if (subs == null || subs.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of("type", "mcpCleanTaskStatus", "data", status));
        } catch (Exception e) {
            log.warn("failed to serialize mcp clean websocket payload. taskId={}", status.getTaskId(), e);
            return;
        }
        for (String sid : subs) {
            WebSocketSession s = sessions.get(sid);
            if (s == null || !s.isOpen()) {
                continue;
            }
            try {
                synchronized (s) {
                    s.sendMessage(new TextMessage(payload));
                }
            } catch (Exception e) {
                log.warn("failed to push mcp clean websocket status. taskId={}, sessionId={}",
                        status.getTaskId(), sid, e);
            }
        }
    }
}

