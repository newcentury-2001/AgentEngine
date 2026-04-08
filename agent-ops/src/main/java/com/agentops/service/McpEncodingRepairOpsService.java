package com.agentops.service;

import com.agentcommon.mcp.McpEncodingRepairResult;
import com.agentcommon.mcp.McpEncodingRepairService;
import com.agentops.config.OpsMcpProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
public class McpEncodingRepairOpsService {

    private final OpsMcpProperties properties;
    private final ObjectMapper objectMapper;

    public McpEncodingRepairOpsService(OpsMcpProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> repairBackupToNewMarkdown() {
        Path src = resolvePath(properties.getRepairSourceJsonPath());
        Path out = resolvePath(properties.getRepairOutputMarkdownPath());

        McpEncodingRepairResult result = McpEncodingRepairService.repairBackupToMarkdown(
                src,
                out,
                objectMapper
        );
        return result.toMap();
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
