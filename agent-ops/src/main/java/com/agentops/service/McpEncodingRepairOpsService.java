package com.agentops.service;

import com.agentcommon.util.CharsetFixUtils;
import com.agentops.config.OpsMcpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class McpEncodingRepairOpsService {

    private static final String DESCRIPTION_CN_FALLBACK = "（中文描述解码失败，请查看原始数据）";

    private final OpsMcpProperties properties;
    private final ObjectMapper objectMapper;

    public McpEncodingRepairOpsService(OpsMcpProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> repairBackupToNewMarkdown() {
        Path src = resolvePath(properties.getRepairSourceJsonPath());
        Path out = resolvePath(properties.getRepairOutputMarkdownPath());
        if (!Files.exists(src)) {
            throw new IllegalArgumentException("repair source json not found: " + src);
        }

        byte[] raw = readAllBytes(src);
        String jsonText = decode(raw);
        JsonNode root = parseJson(jsonText);
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            throw new IllegalStateException("repair source json missing results array");
        }

        int beforeMessy = countMessySegments(jsonText);
        String markdown = buildMarkdown(results);
        int afterMessy = countMessySegments(markdown);
        writeString(out, markdown);

        Map<String, Object> outMap = new LinkedHashMap<>();
        outMap.put("message", "repair markdown generated");
        outMap.put("sourceJsonPath", src.toString());
        outMap.put("outputMarkdownPath", out.toString());
        outMap.put("detectedCharset", "UTF-8");
        outMap.put("totalServers", results.size());
        outMap.put("successCount", countSuccess(results));
        outMap.put("failedCount", results.size() - countSuccess(results));
        outMap.put("messyCountBefore", beforeMessy);
        outMap.put("messyCountAfter", afterMessy);
        return outMap;
    }

    private String buildMarkdown(JsonNode results) {
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
                    String schemaText = repairSchemaText(t.path("inputSchema").asText("{}"));

                    sb.append("### ").append(name).append("\n\n");
                    sb.append("- 描述: ").append(description).append('\n');
                    sb.append("- input_schema:\n\n");
                    sb.append("```json\n").append(schemaText).append("\n```\n\n");
                }
            }
        }
        return sb.toString();
    }

    private String repairSchemaText(String schemaText) {
        String fixed = repairText(schemaText);
        try {
            JsonNode node = objectMapper.readTree(fixed);
            JsonNode repaired = repairJsonTree(node, null);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(repaired);
        } catch (Exception e) {
            return fixed;
        }
    }

    private JsonNode repairJsonTree(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            Map<String, JsonNode> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry ->
                    map.put(entry.getKey(), repairJsonTree(entry.getValue(), entry.getKey()))
            );
            return objectMapper.valueToTree(map);
        }
        if (node.isArray()) {
            List<JsonNode> arr = new ArrayList<>();
            node.forEach(n -> arr.add(repairJsonTree(n, fieldName)));
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

    private boolean isDescriptionField(String key) {
        if (key == null) {
            return false;
        }
        String k = key.trim().toLowerCase();
        return "description".equals(k) || "描述".equals(k);
    }

    private String repairDescription(String text) {
        String fixed = repairText(text).trim();
        if (fixed.isBlank() || !containsCjk(fixed) || CharsetFixUtils.isMessyCode(fixed)) {
            return DESCRIPTION_CN_FALLBACK;
        }
        return fixed;
    }

    private String repairText(String input) {
        if (input == null || input.isBlank()) {
            return input == null ? "" : input;
        }
        return CharsetFixUtils.fixMessyCode(input);
    }

    private boolean containsCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }

    private int countMessySegments(String s) {
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

    private int countSuccess(JsonNode results) {
        int ok = 0;
        for (JsonNode r : results) {
            if (r.path("success").asBoolean(false)) {
                ok++;
            }
        }
        return ok;
    }

    private String decode(byte[] raw) {
        return new String(raw, StandardCharsets.UTF_8);
    }

    private JsonNode parseJson(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (IOException e) {
            throw new IllegalStateException("repair source json parse failed", e);
        }
    }

    private byte[] readAllBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException("read source json failed: " + path, e);
        }
    }

    private void writeString(Path path, String text) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("write repaired markdown failed: " + path, e);
        }
    }

    private Path resolvePath(String configuredPath) {
        String raw = configuredPath == null ? "" : configuredPath.trim();
        if (raw.isBlank()) {
            throw new IllegalArgumentException("path is blank");
        }
        Path p = Path.of(raw);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path byCwd = cwd.resolve(p).normalize();
        if (Files.exists(byCwd) || !raw.startsWith("..")) {
            return byCwd;
        }
        return cwd.resolve("..").resolve(p).normalize();
    }
}

