package com.agentcommon.log.config;

import com.agentcommon.log.LogKafkaTopics;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.log.kafka.producer")
public class LogKafkaProducerProperties {

    private boolean enabled = false;
    private String topic = LogKafkaTopics.LOG_EVENTS;
    private String appName = "agent-engine";
    private String moduleName = "";
    private String envName = "dev";

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

    public String getEnvName() {
        return envName;
    }

    public void setEnvName(String envName) {
        this.envName = envName;
    }
}
