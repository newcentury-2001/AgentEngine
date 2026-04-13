package com.agentcommon.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InputSlot {

    @JsonProperty("slotKey")
    private String slotKey;

    @JsonProperty("field")
    private String field;

    @JsonProperty("fieldType")
    private String fieldType;

    @JsonProperty("required")
    private boolean required;

    @JsonProperty("requirement")
    private String requirement;

    @JsonProperty("condition")
    private String condition;

    @JsonProperty("defaultValueHint")
    private String defaultValueHint;

    @JsonProperty("reason")
    private String reason;

    public String getSlotKey() {
        return slotKey;
    }

    public void setSlotKey(String slotKey) {
        this.slotKey = slotKey;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
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

    public String getRequirement() {
        return requirement;
    }

    public void setRequirement(String requirement) {
        this.requirement = requirement;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getDefaultValueHint() {
        return defaultValueHint;
    }

    public void setDefaultValueHint(String defaultValueHint) {
        this.defaultValueHint = defaultValueHint;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
