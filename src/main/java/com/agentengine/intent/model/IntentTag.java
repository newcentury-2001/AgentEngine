package com.agentengine.intent.model;

public enum IntentTag {
    NONE("none", "无明显意图"),
    STAT("stat", "统计分析"),
    RANK("rank", "排序评估"),
    QUERY("query", "查询检索"),
    ALERT("alert", "告警通知"),
    EXECUTE("execute", "执行操作");

    private final String code;
    private final String description;

    IntentTag(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }

    public static IntentTag fromCode(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        String normalized = value.trim().toLowerCase();
        if ("manage".equals(normalized)) {
            return EXECUTE;
        }
        for (IntentTag tag : values()) {
            if (tag.name().equalsIgnoreCase(normalized) || tag.code.equalsIgnoreCase(normalized)) {
                return tag;
            }
        }
        return NONE;
    }
}
