package com.agentcommon.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OutputSlotInferred {

    @JsonProperty("slotKey")
    private String slotKey;

    public String getSlotKey() {
        return slotKey;
    }

    public void setSlotKey(String slotKey) {
        this.slotKey = slotKey;
    }
}
