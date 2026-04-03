package com.agentengine.intent.model;

public enum ActionType {
    READ("read", "无副作用"),
    WRITE("write", "有副作用");

    private final String code;
    private final String description;

    ActionType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public static ActionType fromCode(String value) {
        if (value == null || value.isBlank()) {
            return READ;
        }
        String normalized = value.trim().toLowerCase();
        for (ActionType actionType : values()) {
            if (actionType.name().equalsIgnoreCase(normalized) || actionType.code.equalsIgnoreCase(normalized)) {
                return actionType;
            }
        }
        return READ;
    }
}
