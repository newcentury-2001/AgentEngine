package com.agentops.mcpclean.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpSummaryCleanTaskStatusView {
    private String taskId;
    private McpCleanTaskState state;
    private Integer totalSkills;
    private Integer processedSkills;
    private Integer successSkills;
    private Integer failedSkills;
    private String currentSkill;
    private String lastError;
    private Integer currentRetryCount;
    private Long createdAtEpochMs;
    private Long updatedAtEpochMs;
    private Long finishedAtEpochMs;
}

