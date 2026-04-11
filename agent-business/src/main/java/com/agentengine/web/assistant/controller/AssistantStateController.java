package com.agentengine.web.assistant.controller;

import com.agentengine.web.assistant.model.AssistantAgentProcessRequest;
import com.agentengine.web.assistant.model.AssistantUserState;
import com.agentengine.web.assistant.service.AssistantAgentOrchestrationService;
import com.agentengine.web.assistant.service.AssistantStateMachineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/assistant/state")
@RequiredArgsConstructor
public class AssistantStateController {

    private final AssistantStateMachineService stateMachineService;
    private final AssistantAgentOrchestrationService orchestrationService;

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody AssistantAgentProcessRequest request) {
        try {
            AssistantUserState state = orchestrationService.execute(request);
            return ResponseEntity.ok(state);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/task")
    public ResponseEntity<?> getByTaskId(@RequestParam String taskId) {
        return stateMachineService.findByTaskId(taskId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "task not found")));
    }

    @GetMapping("/user")
    public ResponseEntity<?> getByUserId(@RequestParam String userId) {
        return stateMachineService.findByUserId(userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "user state not found")));
    }
}
