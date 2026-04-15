package com.agentengine.web.assistant.mq;

public class AssistantToolRetryTaskMessage {
    private String taskId;
    private String intent;
    private String skillName;
    private String toolName;
    private String serverUrl;
    private String argsJson;
    private Integer retryCount;
    private Integer maxRetry;
    private Long createdAtEpochMs;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getArgsJson() {
        return argsJson;
    }

    public void setArgsJson(String argsJson) {
        this.argsJson = argsJson;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(Integer maxRetry) {
        this.maxRetry = maxRetry;
    }

    public Long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }

    public void setCreatedAtEpochMs(Long createdAtEpochMs) {
        this.createdAtEpochMs = createdAtEpochMs;
    }
}

