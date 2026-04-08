package com.agentcommon.log;

import com.agentcommon.log.config.LogKafkaProducerProperties;
import com.agentcommon.log.model.LogEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;

public class LogKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(LogKafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final LogKafkaProducerProperties properties;

    public LogKafkaProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            LogKafkaProducerProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void send(LogEvent event) {
        if (event == null) {
            return;
        }
        fillDefaults(event);
        try {
            String payload = objectMapper.writeValueAsString(event);
            String key = emptyToNull(event.getTaskId());
            kafkaTemplate.send(properties.getTopic(), key, payload);
        } catch (JsonProcessingException e) {
            log.warn("failed to serialize log event", e);
        } catch (Exception e) {
            log.warn("failed to publish log event to kafka", e);
        }
    }

    private void fillDefaults(LogEvent event) {
        if (event.getEventTimeMs() == null || event.getEventTimeMs() <= 0L) {
            event.setEventTimeMs(System.currentTimeMillis());
        }
        if (isBlank(event.getAppName())) {
            event.setAppName(properties.getAppName());
        }
        if (isBlank(event.getModuleName())) {
            event.setModuleName(properties.getModuleName());
        }
        if (isBlank(event.getEnvName())) {
            event.setEnvName(properties.getEnvName());
        }
        if (isBlank(event.getTraceId())) {
            event.setTraceId(MDC.get("traceId"));
        }
        if (isBlank(event.getTaskId())) {
            event.setTaskId(MDC.get("taskId"));
        }
        if (isBlank(event.getServiceName())) {
            event.setServiceName(MDC.get("serviceName"));
        }
        if (isBlank(event.getMethodName())) {
            event.setMethodName(MDC.get("methodName"));
        }
    }

    private String emptyToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
