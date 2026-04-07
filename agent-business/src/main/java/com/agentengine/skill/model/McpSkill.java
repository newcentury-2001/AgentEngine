package com.agentengine.skill.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * MCP技能实体类
 */
@Data
public class McpSkill {

    /**
     * 技能名称
     */
    @JsonProperty("skillName")
    private String skillName;

    /**
     * 技能描述
     */
    @JsonProperty("skillDescription")
    private String skillDescription;

    /**
     * 意图
     */
    private String intent;

    /**
     * 动作类型 (read/write/delete等)
     */
    private String actionType;

    /**
     * 包含的工具列表
     */
    @JsonProperty("tools")
    private List<McpTool> tools;

    /**
     * 技能标签
     * 用于分类和搜索
     */
    private List<String> tags;

    /**
     * 技能 embedding 向量（1024维）
     * 注意：此字段不会从 JSON 反序列化，仅在运行时生成
     */
    @JsonIgnore
    private double[] embedding;
}
