package com.agentengine.skill.preprocess.controller;

import com.agentengine.skill.preprocess.service.SkillVectorStoreService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ops/schema/skill")
public class SkillSchemaOpsController {

    private final SkillVectorStoreService skillVectorStoreService;

    public SkillSchemaOpsController(SkillVectorStoreService skillVectorStoreService) {
        this.skillVectorStoreService = skillVectorStoreService;
    }

    @PostMapping("/init")
    public Map<String, String> init() {
        skillVectorStoreService.initSchemaIfNeeded();
        return Map.of("message", "skill schema initialized");
    }
}

