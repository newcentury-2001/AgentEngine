package com.agentcommon.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InputSlot {

    @JsonProperty("slotKey")
    private String slotKey;

    @JsonProperty("fieldPath")
    private String fieldPath;

    @JsonProperty("fieldType")
    private String fieldType;

    @JsonProperty("required")
    private boolean required;

    public String getSlotKey() {
        return slotKey;
    }

    public void setSlotKey(String slotKey) {
        this.slotKey = slotKey;
    }

    public String getFieldPath() {
        return fieldPath;
    }

    public void setFieldPath(String fieldPath) {
        this.fieldPath = fieldPath;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }
}
