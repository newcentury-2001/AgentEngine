package com.agentengine.skill.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpTool {

    @JsonProperty("toolName")
    private String toolName;

    @JsonProperty("toolDescription")
    private String toolDescription;

    @JsonProperty("inputSchema")
    private Map<String, Object> inputSchema;

    @JsonProperty("inputSlots")
    private List<InputSlot> inputSlots;

    @JsonProperty("outputSlotsInferred")
    private List<OutputSlotInferred> outputSlotsInferred;

    private String skillName;

    @JsonIgnore
    private double[] embedding;
}

