package com.agentcommon.embedding.kafka.config;

import com.agentcommon.kafka.AgentKafkaTopics;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.embedding.kafka.producer")
public class EmbeddingKafkaProducerProperties {

    private boolean enabled = false;
    private String topic = AgentKafkaTopics.EMBEDDING_TASKS;
    private String appName = "agent-engine";
    private String moduleName = "agent-business";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }
}
