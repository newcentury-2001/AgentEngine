package com.agentengine.skill.preprocess.controller;

import com.agentengine.skill.preprocess.model.SkillPreprocessRequest;
import com.agentengine.skill.preprocess.model.SkillInstallResponse;
import com.agentengine.skill.preprocess.model.SkillPreprocessResult;
import com.agentengine.skill.preprocess.service.SkillPreprocessService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skills")
public class SkillPreprocessController {

    private final SkillPreprocessService preprocessService;

    public SkillPreprocessController(SkillPreprocessService preprocessService) {
        this.preprocessService = preprocessService;
    }

    @PostMapping("/preprocess")
    public SkillInstallResponse preprocess(@RequestBody SkillPreprocessRequest request) {
        SkillPreprocessResult result = preprocessService.preprocess(request, true);
        return SkillInstallResponse.from(result);
    }
}
