package com.agentcommon.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

public record McpEncodingRepairResult(
        String message,
        String sourceJsonPath,
        String outputJsonPath,
        String outputMarkdownPath,
        String detectedCharset,
        int totalServers,
        int successCount,
        int failedCount,
        int messyCountBefore,
        int messyCountAfter
) {
    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", message);
        out.put("sourceJsonPath", sourceJsonPath);
        out.put("outputJsonPath", outputJsonPath);
        out.put("outputMarkdownPath", outputMarkdownPath);
        out.put("detectedCharset", detectedCharset);
        out.put("totalServers", totalServers);
        out.put("successCount", successCount);
        out.put("failedCount", failedCount);
        out.put("messyCountBefore", messyCountBefore);
        out.put("messyCountAfter", messyCountAfter);
        return out;
    }
}
