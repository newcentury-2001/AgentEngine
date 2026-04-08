package com.agentcommon.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OutputSlotInferred {

    @JsonProperty("slotKey")
    private String slotKey;

    @JsonProperty("confidence")
    private String confidence;

    public String getSlotKey() {
        return slotKey;
    }

    public void setSlotKey(String slotKey) {
        this.slotKey = slotKey;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }
}
