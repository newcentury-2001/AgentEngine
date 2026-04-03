package com.agentengine.skill.preprocess.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class SkillPromptTemplateConfig {

    private final SkillPromptProperties properties;

    private String toolCleaningTemplate;
    private String toolCompressTemplate;
    private String skillLabelTemplate;

    public SkillPromptTemplateConfig(SkillPromptProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void loadTemplates() {
        this.toolCleaningTemplate = read(properties.getToolCleaningTemplatePath());
        this.toolCompressTemplate = read(properties.getToolCompressTemplatePath());
        this.skillLabelTemplate = read(properties.getSkillLabelTemplatePath());
    }

    public String getToolCleaningTemplate() {
        return toolCleaningTemplate;
    }

    public String getToolCompressTemplate() {
        return toolCompressTemplate;
    }

    public String getSkillLabelTemplate() {
        return skillLabelTemplate;
    }

    private String read(String path) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("read prompt template failed: " + path, e);
        }
    }
}
