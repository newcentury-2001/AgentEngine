package com.agentops.controller;

import com.agentops.service.McpToolListOpsService;
import com.agentops.service.McpEncodingRepairOpsService;
import com.agentops.service.McpSummaryLlmCleanService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ops/mcp")
public class McpOpsController {

    private final McpToolListOpsService mcpToolListOpsService;
    private final McpEncodingRepairOpsService mcpEncodingRepairOpsService;
    private final McpSummaryLlmCleanService mcpSummaryLlmCleanService;

    public McpOpsController(
            McpToolListOpsService mcpToolListOpsService,
            McpEncodingRepairOpsService mcpEncodingRepairOpsService,
            McpSummaryLlmCleanService mcpSummaryLlmCleanService
    ) {
        this.mcpToolListOpsService = mcpToolListOpsService;
        this.mcpEncodingRepairOpsService = mcpEncodingRepairOpsService;
        this.mcpSummaryLlmCleanService = mcpSummaryLlmCleanService;
    }

    @PostMapping("/export-tools-md")
    public Map<String, Object> exportToolsMarkdown(@RequestBody(required = false) List<Object> mcpList) {
        if (mcpList == null || mcpList.isEmpty()) {
            return mcpToolListOpsService.exportToolsListToMarkdown();
        }
        return mcpToolListOpsService.exportToolsListToMarkdown(mcpList);
    }

    @PostMapping("/repair-bck-md")
    public Map<String, Object> repairBackupMarkdown() {
        return mcpEncodingRepairOpsService.repairBackupToNewMarkdown();
    }

    @PostMapping("/normalize-summary-llm")
    public Map<String, Object> normalizeSummaryLlm() {
        return mcpSummaryLlmCleanService.enqueueLlmCleanTasks();
    }
}
