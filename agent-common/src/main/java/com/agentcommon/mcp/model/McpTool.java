package com.agentcommon.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

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

    @JsonProperty("serverUrl")
    @JsonAlias("url")
    private String serverUrl;

    @JsonIgnore
    private double[] embedding;

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolDescription() {
        return toolDescription;
    }

    public void setToolDescription(String toolDescription) {
        this.toolDescription = toolDescription;
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema;
    }

    public List<InputSlot> getInputSlots() {
        return inputSlots;
    }

    public void setInputSlots(List<InputSlot> inputSlots) {
        this.inputSlots = inputSlots;
    }

    public List<OutputSlotInferred> getOutputSlotsInferred() {
        return outputSlotsInferred;
    }

    public void setOutputSlotsInferred(List<OutputSlotInferred> outputSlotsInferred) {
        this.outputSlotsInferred = outputSlotsInferred;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public double[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(double[] embedding) {
        this.embedding = embedding;
    }
}
