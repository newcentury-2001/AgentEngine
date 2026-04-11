package com.agentengine.skill.embedding.kafka.model;

import com.agentengine.skill.embedding.model.vo.EmbeddingResultExtended;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmbeddingTaskStatusView {
    private String taskId;
    private String taskType;
    private EmbeddingTaskState state;
    private Integer currentBatchNo;
    private Integer totalBatches;
    private long createdAtEpochMs;
    private Long startedAtEpochMs;
    private Long finishedAtEpochMs;
    private String errorMessage;
    private EmbeddingResultExtended result;
}
