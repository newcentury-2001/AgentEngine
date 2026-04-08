package com.agentcommon.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class McpSkill {

    @JsonProperty("skillName")
    private String skillName;

    @JsonProperty("skillDescription")
    private String skillDescription;

    @JsonProperty("serverUrl")
    private String serverUrl;

    private String intent;

    private String actionType;

    @JsonProperty("tools")
    private List<McpTool> tools;

    private List<String> tags;

    @JsonIgnore
    private double[] embedding;

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getSkillDescription() {
        return skillDescription;
    }

    public void setSkillDescription(String skillDescription) {
        this.skillDescription = skillDescription;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public List<McpTool> getTools() {
        return tools;
    }

    public void setTools(List<McpTool> tools) {
        this.tools = tools;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public double[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(double[] embedding) {
        this.embedding = embedding;
    }
}
