package com.agentcommon.http;

import com.agentcommon.util.CharsetFixUtils;

public class EncodingRepairEngine {

    public String repairIfNeeded(String content) {
        if (!shouldRepair(content)) {
            return content;
        }
        return CharsetFixUtils.fixMessyCode(content);
    }

    public boolean shouldRepair(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        if (isJson(content)) {
            return hasMessyInJson(content);
        }
        return CharsetFixUtils.isMessyCode(content);
    }

    private boolean isJson(String content) {
        String trimmed = content.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private boolean hasMessyInJson(String content) {
        return extractStringLiterals(content).stream()
                .anyMatch(CharsetFixUtils::isMessyCode);
    }

    private java.util.List<String> extractStringLiterals(String json) {
        java.util.List<String> literals = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escape) {
                if (inString) {
                    current.append(c);
                }
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                if (inString) {
                    current.append(c);
                }
                continue;
            }

            if (c == '"') {
                if (inString) {
                    literals.add(current.toString());
                    current.setLength(0);
                }
                inString = !inString;
                continue;
            }

            if (inString) {
                current.append(c);
            }
        }
        return literals;
    }
}
