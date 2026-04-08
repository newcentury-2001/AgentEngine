package com.agentlog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agent.log.db-executor")
public class DbPersistExecutorProperties {

    private int queueSize = 500;
    private int qpsLimitPerPartition = 200;
    private String threadNamePrefix = "log-db-persist";

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getQpsLimitPerPartition() {
        return qpsLimitPerPartition;
    }

    public void setQpsLimitPerPartition(int qpsLimitPerPartition) {
        this.qpsLimitPerPartition = qpsLimitPerPartition;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }
}
