package com.agentops.controller;

import com.agentops.service.McpToolListOpsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ops/mcp")
public class McpOpsController {

    private final McpToolListOpsService mcpToolListOpsService;

    public McpOpsController(McpToolListOpsService mcpToolListOpsService) {
        this.mcpToolListOpsService = mcpToolListOpsService;
    }

    @PostMapping("/export-tools-md")
    public Map<String, Object> exportToolsMarkdown() {
        return mcpToolListOpsService.exportToolsListToMarkdown();
    }
}

