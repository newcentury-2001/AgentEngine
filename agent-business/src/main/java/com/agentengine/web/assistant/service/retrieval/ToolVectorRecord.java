package com.agentengine.web.assistant.service.retrieval;

import com.agentcommon.mcp.model.InputSlot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolVectorRecord {
    private String skillName;
    private String toolName;
    private String toolDescription;
    private String serverUrl;
    private String toolUrl;
    private List<InputSlot> inputSlots;
    private double[] vector;
    private double heatWeight;
}
