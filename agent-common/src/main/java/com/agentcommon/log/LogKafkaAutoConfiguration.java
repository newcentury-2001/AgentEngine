package com.agentcommon.log;

import com.agentcommon.log.config.LogKafkaProducerProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.kafka.core.KafkaTemplate;

@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties(LogKafkaProducerProperties.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class LogKafkaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnProperty(prefix = "agent.log.kafka.producer", name = "enabled", havingValue = "true")
    public LogKafkaProducer logKafkaProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            LogKafkaProducerProperties properties) {
        return new LogKafkaProducer(kafkaTemplate, objectMapper, properties);
    }

    @Bean
    @ConditionalOnBean(LogKafkaProducer.class)
    @ConditionalOnMissingBean
    public ExceptionKafkaLogAspect exceptionKafkaLogAspect(LogKafkaProducer logKafkaProducer) {
        return new ExceptionKafkaLogAspect(logKafkaProducer);
    }
}
