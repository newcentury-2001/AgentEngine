package com.agentengine.web.assistant.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AssistantToolRetryPublisher {
    private final ObjectMapper objectMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final String topic;
    private final int sendTimeoutMs;
    private final int publishRetryMaxAttempts;
    private final long publishRetrySleepMs;
    private final int[] retryDelayLevels;

    public AssistantToolRetryPublisher(
            ObjectMapper objectMapper,
            ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider,
            @Value("${agent.assistant.tool-http.retry.topic:assistant_tool_retry_tasks}") String topic,
            @Value("${agent.assistant.tool-http.retry.producer.send-timeout-ms:3000}") int sendTimeoutMs,
            @Value("${agent.assistant.tool-http.retry.producer.retry-delay-levels:1,2,3}") String retryDelayLevels,
            @Value("${agent.assistant.tool-http.retry.producer.publish-retry-max-attempts:3}") int publishRetryMaxAttempts,
            @Value("${agent.assistant.tool-http.retry.producer.publish-retry-sleep-ms:200}") long publishRetrySleepMs) {
        this.objectMapper = objectMapper;
        this.rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
        this.topic = topic;
        this.sendTimeoutMs = sendTimeoutMs;
        this.publishRetryMaxAttempts = Math.max(1, publishRetryMaxAttempts);
        this.publishRetrySleepMs = Math.max(0L, publishRetrySleepMs);
        this.retryDelayLevels = parseDelayLevels(retryDelayLevels);
    }

    public boolean sendWithDelayRetry(AssistantToolRetryTaskMessage message, int retryCount) {
        int delayLevel = resolveDelayLevel(retryCount);
        return sendWithRetryInternal(message, delayLevel);
    }

    private boolean sendWithRetryInternal(AssistantToolRetryTaskMessage message, int delayLevel) {
        for (int i = 1; i <= publishRetryMaxAttempts; i++) {
            if (sendInternal(message, delayLevel)) {
                return true;
            }
            sleepSilently(publishRetrySleepMs);
        }
        return false;
    }

    private boolean sendInternal(AssistantToolRetryTaskMessage message, int delayLevel) {
        if (rocketMQTemplate == null) {
            log.warn("rocketMQTemplate not available, skip assistant delayed retry. taskId={}, tool={}",
                    message == null ? "-" : message.getTaskId(),
                    message == null ? "-" : message.getToolName());
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
            log.error("failed to publish assistant delayed retry task. taskId={}, tool={}, topic={}, delayLevel={}",
                    message == null ? "-" : message.getTaskId(),
                    message == null ? "-" : message.getToolName(),
                    topic, delayLevel, e);
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
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .mapToInt(Integer::parseInt)
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

