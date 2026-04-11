package com.agentops.mcpclean.model;

import java.util.List;
import java.util.Map;

public class McpSummaryCleanTaskMessage {
    private String taskId;
    private String skillName;
    private List<String> pendingToolNames;
    private Map<String, List<String>> slotMissHints;
    private Boolean skillPending;
    private Integer retryCount;
    private Integer maxRetry;
    private Long createdAtEpochMs;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public List<String> getPendingToolNames() {
        return pendingToolNames;
    }

    public void setPendingToolNames(List<String> pendingToolNames) {
        this.pendingToolNames = pendingToolNames;
    }

    public Map<String, List<String>> getSlotMissHints() {
        return slotMissHints;
    }

    public void setSlotMissHints(Map<String, List<String>> slotMissHints) {
        this.slotMissHints = slotMissHints;
    }

    public Boolean getSkillPending() {
        return skillPending;
    }

    public void setSkillPending(Boolean skillPending) {
        this.skillPending = skillPending;
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
