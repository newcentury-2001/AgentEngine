package com.agentlog.service;

import com.agentcommon.log.model.LogEvent;
import com.agentlog.config.LogConsumerProperties;
import com.agentlog.repository.LogEventRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LogEventConsumeService {

    private final LogEventRepository repository;
    private final LogConsumerProperties properties;

    public LogEventConsumeService(LogEventRepository repository, LogConsumerProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public void consumeBatch(List<LogEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        int batchSize = Math.max(1, properties.getBatchSize());
        if (events.size() <= batchSize) {
            repository.batchInsert(events);
            return;
        }

        List<LogEvent> one = new ArrayList<>(batchSize);
        for (LogEvent event : events) {
            one.add(event);
            if (one.size() >= batchSize) {
                repository.batchInsert(one);
                one.clear();
            }
        }
        if (!one.isEmpty()) {
            repository.batchInsert(one);
        }
    }
}
