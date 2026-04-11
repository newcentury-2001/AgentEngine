package com.agentcommon.mcp;

import com.agentcommon.util.CharsetFixUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class McpEncodingRepairService {

    private static final String DESCRIPTION_CN_FALLBACK = "(description decode failed, please check original text)";

    private McpEncodingRepairService() {
    }

    public static McpEncodingRepairResult repairBackupToMarkdown(
            Path src,
            Path outJson,
            Path out,
            ObjectMapper objectMapper
    ) {
        if (!Files.exists(src)) {
            throw new IllegalArgumentException("repair source json not found: " + src);
        }

        byte[] raw = readAllBytes(src);
        String jsonText = decode(raw);
        JsonNode root = parseJson(jsonText, objectMapper);
        JsonNode repairedRoot = repairJsonTree(root, null, objectMapper);
        JsonNode results = repairedRoot.path("results");
        if (!results.isArray()) {
            throw new IllegalStateException("repair source json missing results array");
        }

        int beforeMessy = countMessySegments(jsonText);
        String repairedJsonText = toPrettyJson(repairedRoot, objectMapper);
        int afterMessyJson = countMessySegments(repairedJsonText);
        String markdown = buildMarkdown(results, objectMapper);
        int afterMessy = countMessySegments(markdown);
        writeString(outJson, repairedJsonText);
        writeString(out, markdown);

        int successCount = countSuccess(results);
        return new McpEncodingRepairResult(
                "repair markdown generated",
                src.toString(),
                outJson.toString(),
                out.toString(),
                "UTF-8",
                results.size(),
                successCount,
                results.size() - successCount,
                beforeMessy,
                Math.min(afterMessy, afterMessyJson)
        );
    }

    private static String buildMarkdown(JsonNode results, ObjectMapper objectMapper) {
        StringBuilder sb = new StringBuilder();
        sb.append("# MCP tools/list Export (repaired)\n\n");
        sb.append("- GeneratedAt: ").append(LocalDateTime.now()).append('\n');
        sb.append("- Total: ").append(results.size()).append('\n');
        sb.append("- Success: ").append(countSuccess(results)).append('\n');
        sb.append("- Failed: ").append(results.size() - countSuccess(results)).append("\n\n");

        for (JsonNode r : results) {
            String serverLabel = repairText(r.path("serverLabel").asText(""));
            String serverUrl = repairText(r.path("serverUrl").asText(""));
            boolean success = r.path("success").asBoolean(false);
            String errorMessage = repairText(r.path("errorMessage").asText(""));
            JsonNode tools = r.path("tools");

            sb.append("## ").append(serverLabel).append("\n\n");
            sb.append("- URL: `").append(serverUrl).append("`\n");
            sb.append("- Status: ").append(success ? "success" : "failed").append('\n');
            if (!success) {
                sb.append("- Error: ").append(errorMessage).append("\n\n");
                continue;
            }
            sb.append("- ToolCount: ").append(tools.isArray() ? tools.size() : 0).append("\n\n");

            if (tools.isArray()) {
                for (JsonNode t : tools) {
                    String name = repairText(t.path("name").asText(""));
                    String description = repairDescription(t.path("description").asText(""));
                    String schemaText = repairSchemaText(t.path("inputSchema").asText("{}"), objectMapper);

                    sb.append("### ").append(name).append("\n\n");
                    sb.append("- Description: ").append(description).append('\n');
                    sb.append("- input_schema:\n\n");
                    sb.append("```json\n").append(schemaText).append("\n```\n\n");
                }
            }
        }
        return sb.toString();
    }

    private static String repairSchemaText(String schemaText, ObjectMapper objectMapper) {
        String fixed = repairText(schemaText);
        try {
            JsonNode node = objectMapper.readTree(fixed);
            JsonNode repaired = repairJsonTree(node, null, objectMapper);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(repaired);
        } catch (Exception e) {
            return fixed;
        }
    }

    private static JsonNode repairJsonTree(JsonNode node, String fieldName, ObjectMapper objectMapper) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            Map<String, JsonNode> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry ->
                    map.put(entry.getKey(), repairJsonTree(entry.getValue(), entry.getKey(), objectMapper))
            );
            return objectMapper.valueToTree(map);
        }
        if (node.isArray()) {
            List<JsonNode> arr = new ArrayList<>();
            node.forEach(n -> arr.add(repairJsonTree(n, fieldName, objectMapper)));
            return objectMapper.valueToTree(arr);
        }
        if (node.isTextual()) {
            String fixed = repairText(node.asText(""));
            if (isDescriptionField(fieldName)) {
                fixed = repairDescription(fixed);
            }
            return objectMapper.getNodeFactory().textNode(fixed);
        }
        return node;
    }

    private static boolean isDescriptionField(String key) {
        if (key == null) {
            return false;
        }
        String k = key.trim().toLowerCase();
        return "description".equals(k) || "描述".equals(k);
    }

    private static String repairDescription(String text) {
        String fixed = repairText(text).trim();
        if (fixed.isBlank() || !containsCjk(fixed) || CharsetFixUtils.isMessyCode(fixed)) {
            return DESCRIPTION_CN_FALLBACK;
        }
        return fixed;
    }

    private static String repairText(String input) {
        if (input == null || input.isBlank()) {
            return input == null ? "" : input;
        }
        return CharsetFixUtils.fixMessyCode(input);
    }

    private static boolean containsCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }

    private static int countMessySegments(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        int count = 0;
        String[] parts = s.split("[\\r\\n\\t ]+");
        for (String p : parts) {
            if (!p.isBlank() && CharsetFixUtils.isMessyCode(p)) {
                count++;
            }
        }
        return count;
    }

    private static int countSuccess(JsonNode results) {
        int ok = 0;
        for (JsonNode r : results) {
            if (r.path("success").asBoolean(false)) {
                ok++;
            }
        }
        return ok;
    }

    private static String decode(byte[] raw) {
        return new String(raw, StandardCharsets.UTF_8);
    }

    private static String toPrettyJson(JsonNode root, ObjectMapper objectMapper) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("serialize repaired json failed", e);
        }
    }

    private static JsonNode parseJson(String text, ObjectMapper objectMapper) {
        try {
            return objectMapper.readTree(text);
        } catch (IOException e) {
            throw new IllegalStateException("repair source json parse failed", e);
        }
    }

    private static byte[] readAllBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException("read source json failed: " + path, e);
        }
    }

    private static void writeString(Path path, String text) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("write repaired file failed: " + path, e);
        }
    }
}
