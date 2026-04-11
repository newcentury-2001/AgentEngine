package com.agentops.mcpclean;

import com.agentops.mcpclean.model.McpSummaryCleanTaskMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class McpSummaryCleanTaskPublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String topic;
    private final int sendTimeoutMs;
    private final int maxRetry;
    private final int[] retryDelayLevels;
    private final int publishRetryMaxAttempts;
    private final long publishRetrySleepMs;

    public McpSummaryCleanTaskPublisher(
            RocketMQTemplate rocketMQTemplate,
            ObjectMapper objectMapper,
            @Value("${agent.mcp-cleaner.rocketmq.producer.enabled:true}") boolean enabled,
            @Value("${agent.mcp-cleaner.rocketmq.producer.topic:agent_mcp_summary_clean_tasks}") String topic,
            @Value("${agent.mcp-cleaner.rocketmq.producer.send-timeout-ms:3000}") int sendTimeoutMs,
            @Value("${agent.mcp-cleaner.rocketmq.producer.max-retry:6}") int maxRetry,
            @Value("${agent.mcp-cleaner.rocketmq.producer.retry-delay-levels:2,3,4,5,6,7}") String retryDelayLevels,
            @Value("${agent.mcp-cleaner.rocketmq.producer.publish-retry-max-attempts:3}") int publishRetryMaxAttempts,
            @Value("${agent.mcp-cleaner.rocketmq.producer.publish-retry-sleep-ms:200}") long publishRetrySleepMs) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.topic = topic;
        this.sendTimeoutMs = sendTimeoutMs;
        this.maxRetry = Math.max(0, maxRetry);
        this.retryDelayLevels = parseDelayLevels(retryDelayLevels);
        this.publishRetryMaxAttempts = Math.max(1, publishRetryMaxAttempts);
        this.publishRetrySleepMs = Math.max(0L, publishRetrySleepMs);
    }

    public int maxRetry() {
        return maxRetry;
    }

    public boolean sendWithRetry(McpSummaryCleanTaskMessage message) {
        return sendWithRetryInternal(message, 0);
    }

    public boolean sendWithDelayRetry(McpSummaryCleanTaskMessage message, int retryCount) {
        int delayLevel = resolveDelayLevel(retryCount);
        return sendWithRetryInternal(message, delayLevel);
    }

    private boolean sendWithRetryInternal(McpSummaryCleanTaskMessage message, int delayLevel) {
        int attempt = 0;
        while (attempt < publishRetryMaxAttempts) {
            attempt++;
            if (sendInternal(message, delayLevel)) {
                return true;
            }
            sleepSilently(publishRetrySleepMs * attempt);
        }
        return false;
    }

    private boolean sendInternal(McpSummaryCleanTaskMessage message, int delayLevel) {
        if (!enabled) {
            log.warn("mcp summary clean producer disabled, skip publish. taskId={}", message.getTaskId());
            return false;
        }
        try {
            String payload = objectMapper.writeValueAsString(message);
            if (delayLevel > 0) {
                Message<String> msg = MessageBuilder.withPayload(payload).build();
                rocketMQTemplate.syncSend(topic, msg, sendTimeoutMs, delayLevel);
            } else {
                rocketMQTemplate.syncSend(topic, payload, sendTimeoutMs);
            }
            return true;
        } catch (Exception e) {
            log.error("failed to publish mcp clean task. taskId={}, skill={}, topic={}, delayLevel={}",
                    message.getTaskId(), message.getSkillName(), topic, delayLevel, e);
            return false;
        }
    }

    private int resolveDelayLevel(int retryCount) {
        if (retryDelayLevels.length == 0) {
            return 0;
        }
        int idx = Math.max(0, retryCount - 1);
        if (idx >= retryDelayLevels.length) {
            idx = retryDelayLevels.length - 1;
        }
        return retryDelayLevels[idx];
    }

    private int[] parseDelayLevels(String raw) {
        if (raw == null || raw.isBlank()) {
            return new int[0];
        }
        String[] arr = raw.split(",");
        java.util.List<Integer> levels = new java.util.ArrayList<>();
        for (String one : arr) {
            try {
                int v = Integer.parseInt(one.trim());
                if (v > 0) levels.add(v);
            } catch (Exception ignored) {
            }
        }
        return levels.stream().mapToInt(Integer::intValue).toArray();
    }

    private void sleepSilently(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}

