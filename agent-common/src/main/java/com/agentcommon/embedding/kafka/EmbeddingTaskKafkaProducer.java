package com.agentcommon.embedding.kafka;

import com.agentcommon.embedding.kafka.config.EmbeddingKafkaProducerProperties;
import com.agentcommon.embedding.kafka.model.EmbeddingTaskMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

public class EmbeddingTaskKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingTaskKafkaProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final EmbeddingKafkaProducerProperties properties;

    public EmbeddingTaskKafkaProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            EmbeddingKafkaProducerProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void send(EmbeddingTaskMessage message) {
        if (message == null) {
            return;
        }
        try {
            String key = message.getTaskId();
            String payload = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(properties.getTopic(), key, payload);
        } catch (Exception e) {
            log.warn("failed to publish embedding task to kafka", e);
        }
    }
}
