package com.agentengine.web.assistant.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantExecutionPlan {
    private String taskId;
    private String userId;
    private String intent;
    private String skillName;
    private List<AssistantPlannedTool> selectedTools;
    private List<AssistantPlannedTool> pendingTools;
    private List<AssistantPlannedTool> executedTools;
    private Set<String> slotScope;
    private List<String> missingSlots;
    private Map<String, String> toolOutputSummaries;
}
