package com.agentengine.skill.embedding.kafka;

import com.agentcommon.embedding.kafka.model.EmbeddingTaskMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmbeddingTaskPublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String topic;
    private final int sendTimeoutMs;
    private final int maxRetry;
    private final int[] retryDelayLevels;
    private final int publishRetryMaxAttempts;
    private final long publishRetrySleepMs;

    public EmbeddingTaskPublisher(
            RocketMQTemplate rocketMQTemplate,
            ObjectMapper objectMapper,
            @Value("${agent.embedding.rocketmq.producer.enabled:false}") boolean enabled,
            @Value("${agent.embedding.rocketmq.producer.topic:agent_embedding_tasks}") String topic,
            @Value("${agent.embedding.rocketmq.producer.send-timeout-ms:3000}") int sendTimeoutMs,
            @Value("${agent.embedding.rocketmq.producer.max-retry:6}") int maxRetry,
            @Value("${agent.embedding.rocketmq.producer.retry-delay-levels:2,3,4,5,6,7}") String retryDelayLevels,
            @Value("${agent.embedding.rocketmq.producer.publish-retry-max-attempts:3}") int publishRetryMaxAttempts,
            @Value("${agent.embedding.rocketmq.producer.publish-retry-sleep-ms:200}") long publishRetrySleepMs) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.topic = topic;
        this.sendTimeoutMs = Math.max(1000, sendTimeoutMs);
        this.maxRetry = Math.max(0, maxRetry);
        this.retryDelayLevels = parseDelayLevels(retryDelayLevels);
        this.publishRetryMaxAttempts = Math.max(1, publishRetryMaxAttempts);
        this.publishRetrySleepMs = Math.max(0L, publishRetrySleepMs);
    }

    public boolean send(EmbeddingTaskMessage message) {
        // 实时消息：不带延时级别，立即写入 broker。
        return sendInternal(message, 0);
    }

    public boolean sendWithRetry(EmbeddingTaskMessage message) {
        return sendWithRetryInternal(message, 0);
    }

    public boolean sendWithDelay(EmbeddingTaskMessage message, int retryCount) {
        // 重试消息：根据重试次数映射 RocketMQ delayLevel，进入延时队列。
        int delayLevel = resolveDelayLevel(retryCount);
        return sendInternal(message, delayLevel);
    }

    public boolean sendWithDelayRetry(EmbeddingTaskMessage message, int retryCount) {
        int delayLevel = resolveDelayLevel(retryCount);
        return sendWithRetryInternal(message, delayLevel);
    }

    public int maxRetry() {
        return maxRetry;
    }

    private boolean sendInternal(EmbeddingTaskMessage message, int delayLevel) {
        if (!enabled || message == null) {
            return false;
        }
        try {
            // 统一序列化为 JSON，便于消费端按同一模型反序列化。
            String payload = objectMapper.writeValueAsString(message);
            if (delayLevel > 0) {
                Message<String> msg = MessageBuilder.withPayload(payload).build();
                // 关键主链路：同步发送到 RocketMQ broker（带延时等级）。
                rocketMQTemplate.syncSend(topic, msg, sendTimeoutMs, delayLevel);
            } else {
                // 关键主链路：同步发送到 RocketMQ broker（立即投递）。
                rocketMQTemplate.syncSend(topic, payload, sendTimeoutMs);
            }
            return true;
        } catch (Exception e) {
            log.error("failed to publish embedding task. taskId={}, topic={}, delayLevel={}",
                    message.getTaskId(), topic, delayLevel, e);
            return false;
        }
    }

    private boolean sendWithRetryInternal(EmbeddingTaskMessage message, int delayLevel) {
        for (int attempt = 1; attempt <= publishRetryMaxAttempts; attempt++) {
            if (sendInternal(message, delayLevel)) {
                return true;
            }
            if (attempt < publishRetryMaxAttempts) {
                sleepSilently(publishRetrySleepMs * attempt);
            }
        }
        return false;
    }

    private int resolveDelayLevel(int retryCount) {
        if (retryDelayLevels.length == 0) {
            return 0;
        }
        // 重试次数超过配置上限时，复用最后一个延时等级。
        int idx = Math.max(0, retryCount - 1);
        if (idx >= retryDelayLevels.length) {
            idx = retryDelayLevels.length - 1;
        }
        return retryDelayLevels[idx];
    }

    private int[] parseDelayLevels(String raw) {
        if (raw == null || raw.isBlank()) {
            return new int[] {2, 3, 4, 5, 6, 7};
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .mapToInt(Integer::parseInt)
                .filter(v -> v > 0)
                .toArray();
    }

    private void sleepSilently(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
