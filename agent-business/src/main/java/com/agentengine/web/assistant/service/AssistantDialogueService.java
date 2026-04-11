package com.agentengine.web.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssistantDialogueService {

    private static final String USER_CHAT_KEY_PREFIX = "assistant:chat:user:";
    private static final int MAX_USER_MESSAGES = 2;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${agent.assistant.state.ttl-minutes:30}")
    private long ttlMinutes;

    public void appendUserMessage(String taskId, String content) {
        if (blank(taskId) || blank(content)) {
            return;
        }
        try {
            DialogueMessage msg = new DialogueMessage("user", content, System.currentTimeMillis());
            String raw = objectMapper.writeValueAsString(msg);
            String key = userKey(taskId);
            stringRedisTemplate.opsForList().rightPush(key, raw);
            stringRedisTemplate.opsForList().trim(key, -MAX_USER_MESSAGES, -1);
            stringRedisTemplate.expire(key, ttlMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("failed to append assistant user message. taskId={}", taskId, e);
        }
    }

    public List<DialogueMessage> recentUserMessages(String taskId, int limit) {
        if (blank(taskId) || limit <= 0) {
            return Collections.emptyList();
        }
        List<String> items = stringRedisTemplate.opsForList().range(userKey(taskId), -limit, -1);
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().map(this::parse).collect(Collectors.toList());
    }

    private String userKey(String taskId) {
        return USER_CHAT_KEY_PREFIX + taskId;
    }

    private boolean blank(String text) {
        return text == null || text.trim().isEmpty();
    }

    @Data
    @AllArgsConstructor
    public static class DialogueMessage {
        private String role;
        private String content;
        private long ts;
    }

    private DialogueMessage parse(String raw) {
        try {
            return objectMapper.readValue(raw, DialogueMessage.class);
        } catch (Exception e) {
            return new DialogueMessage("unknown", raw, 0L);
        }
    }
}
