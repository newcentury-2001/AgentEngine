package com.agentengine.web.assistant.model;

import com.agentcommon.mcp.model.InputSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantPlannedTool {
    private String toolName;
    private String toolDescription;
    private String serverUrl;
    private String toolUrl;
    private List<InputSlot> inputSlots;
    private List<String> requiredSlots;
    private List<String> optionalSlots;
    private Double simScore;
    private Double heatWeight;
}
