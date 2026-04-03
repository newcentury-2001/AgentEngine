package com.agentengine.skill.preprocess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "skill.prompt")
public class SkillPromptProperties {

    private String toolCleaningTemplatePath;
    private String toolCompressTemplatePath;
    private String skillLabelTemplatePath;

    public String getToolCleaningTemplatePath() {
        return toolCleaningTemplatePath;
    }

    public void setToolCleaningTemplatePath(String toolCleaningTemplatePath) {
        this.toolCleaningTemplatePath = toolCleaningTemplatePath;
    }

    public String getToolCompressTemplatePath() {
        return toolCompressTemplatePath;
    }

    public void setToolCompressTemplatePath(String toolCompressTemplatePath) {
        this.toolCompressTemplatePath = toolCompressTemplatePath;
    }

    public String getSkillLabelTemplatePath() {
        return skillLabelTemplatePath;
    }

    public void setSkillLabelTemplatePath(String skillLabelTemplatePath) {
        this.skillLabelTemplatePath = skillLabelTemplatePath;
    }
}
