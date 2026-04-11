package com.agentops.service;

import com.agentcommon.mcp.McpEncodingRepairResult;
import com.agentcommon.mcp.McpEncodingRepairService;
import com.agentops.config.OpsMcpProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
        Path outJson = resolvePath(properties.getRepairOutputJsonPath());
        Path out = resolvePath(properties.getRepairOutputMarkdownPath());

        McpEncodingRepairResult result = McpEncodingRepairService.repairBackupToMarkdown(
                src,
                outJson,
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
        Path cwd = Path.of(System.getProperty("user.dir")).normalize();
        String rel = raw.replace("\\", "/");
        if (rel.startsWith("./")) {
            rel = rel.substring(2);
        }
        List<Path> candidates = new ArrayList<>();
        candidates.add(cwd.resolve(p).normalize());
        if (rel.startsWith("AgentEngine/")) {
            candidates.add(cwd.resolve(rel.substring("AgentEngine/".length())).normalize());
        } else {
            candidates.add(cwd.resolve("AgentEngine").resolve(rel).normalize());
        }
        candidates.add(cwd.resolve("..").resolve(p).normalize());
        if (!rel.startsWith("AgentEngine/")) {
            candidates.add(cwd.resolve("..").resolve("AgentEngine").resolve(rel).normalize());
        }
        for (Path one : candidates) {
            if (Files.exists(one)) {
                return one;
            }
        }
        for (Path one : candidates) {
            Path parent = one.getParent();
            if (parent != null && Files.exists(parent)) {
                return one;
            }
        }
        return candidates.get(0);
    }
}
