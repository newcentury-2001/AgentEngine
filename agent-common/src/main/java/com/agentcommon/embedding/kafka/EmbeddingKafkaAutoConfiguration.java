package com.agentcommon.embedding.kafka;

import com.agentcommon.embedding.kafka.config.EmbeddingKafkaProducerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties(EmbeddingKafkaProducerProperties.class)
public class EmbeddingKafkaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnProperty(prefix = "agent.embedding.kafka.producer", name = "enabled", havingValue = "true")
    public EmbeddingTaskKafkaProducer embeddingTaskKafkaProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            EmbeddingKafkaProducerProperties properties) {
        return new EmbeddingTaskKafkaProducer(kafkaTemplate, objectMapper, properties);
    }
}
