package com.agentlog.consumer;

import com.agentcommon.log.model.LogEvent;
import com.agentlog.config.LogConsumerProperties;
import com.agentlog.service.LogEventConsumeService;
import com.agentlog.service.PartitionDbExecutorRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class KafkaLogEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaLogEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final LogEventConsumeService consumeService;
    private final LogConsumerProperties properties;
    private final PartitionDbExecutorRouter executorRouter;

    public KafkaLogEventConsumer(
            ObjectMapper objectMapper,
            LogEventConsumeService consumeService,
            LogConsumerProperties properties,
            PartitionDbExecutorRouter executorRouter) {
        this.objectMapper = objectMapper;
        this.consumeService = consumeService;
        this.properties = properties;
        this.executorRouter = executorRouter;
    }

    @KafkaListener(
            topics = "#{@logConsumerProperties.topic}",
            groupId = "#{@logConsumerProperties.groupId}",
            concurrency = "#{@logConsumerProperties.concurrency}",
            containerFactory = "kafkaBatchListenerContainerFactory",
            autoStartup = "#{@logConsumerProperties.enabled}"
    )
    public void onMessage(List<ConsumerRecord<String, String>> records, Acknowledgment acknowledgment) {
        if (records == null || records.isEmpty()) {
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            return;
        }

        Map<Integer, List<LogEvent>> eventsByPartition = new LinkedHashMap<>();
        for (ConsumerRecord<String, String> record : records) {
            LogEvent event = parse(record.value());
            event.setKafkaTopic(record.topic());
            event.setKafkaPartition(record.partition());
            event.setKafkaOffset(record.offset());
            eventsByPartition.computeIfAbsent(record.partition(), p -> new ArrayList<>()).add(event);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>(eventsByPartition.size());
        for (Map.Entry<Integer, List<LogEvent>> one : eventsByPartition.entrySet()) {
            int partition = one.getKey();
            List<LogEvent> events = one.getValue();
            futures.add(executorRouter.submit(partition, () -> consumeService.consumeBatch(events)));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }

    private LogEvent parse(String raw) {
        try {
            LogEvent parsed = objectMapper.readValue(raw, LogEvent.class);
            if (parsed.getMessage() == null || parsed.getMessage().isBlank()) {
                parsed.setMessage(raw == null ? "" : raw);
            }
            return parsed;
        } catch (Exception ex) {
            log.warn("failed to parse log event, fallback to raw payload", ex);
            LogEvent fallback = new LogEvent();
            fallback.setLevel("ERROR");
            fallback.setEventType("PARSER_ERROR");
            fallback.setMessage(raw == null ? "" : raw);
            fallback.setExceptionClass(ex.getClass().getName());
            fallback.setStackTrace(ex.getMessage());
            return fallback;
        }
    }
}
