package com.agentengine.skill.embedding.websocket;

import com.agentengine.skill.embedding.kafka.model.EmbeddingTaskStatusView;
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
public class EmbeddingTaskWebSocketBroadcaster {

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
            if (subs != null) {
                subs.remove(session.getId());
                if (subs.isEmpty()) {
                    taskSubscribers.remove(taskId);
                }
            }
        }
    }

    public void subscribe(WebSocketSession session, String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        String sid = session.getId();
        // 正向索引：session -> taskIds（断开连接时便于反查并清理订阅关系）。
        sessionTaskIds.computeIfAbsent(sid, k -> ConcurrentHashMap.newKeySet()).add(taskId);
        // 反向索引：taskId -> sessions（发布状态时可快速定位订阅该任务的连接）。
        taskSubscribers.computeIfAbsent(taskId, k -> ConcurrentHashMap.newKeySet()).add(sid);
    }

    public void publish(EmbeddingTaskStatusView status) {
        if (status == null || status.getTaskId() == null) {
            return;
        }
        // 先按 taskId 找订阅者，只向关注该任务的连接做定向推送。
        Set<String> subs = taskSubscribers.get(status.getTaskId());
        if (subs == null || subs.isEmpty()) {
            return;
        }
        String payload;
        try {
            // 统一消息格式：{type, data}，前端可按 type 分发处理逻辑。
            payload = objectMapper.writeValueAsString(Map.of("type", "taskStatus", "data", status));
        } catch (Exception e) {
            log.warn("failed to serialize websocket payload. taskId={}", status.getTaskId(), e);
            return;
        }

        for (String sid : subs) {
            WebSocketSession s = sessions.get(sid);
            if (s == null || !s.isOpen()) {
                continue;
            }
            try {
                // 同一 session 串行发送，避免并发 write 导致 websocket 状态异常。
                synchronized (s) {
                    s.sendMessage(new TextMessage(payload));
                }
            } catch (Exception e) {
                log.warn("failed to push websocket task status. taskId={}, sessionId={}",
                        status.getTaskId(), sid, e);
            }
        }
    }
}
