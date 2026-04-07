package com.agentengine.skill.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * MCP工具实体类
 */
@Data
public class McpTool {

    /**
     * 工具名称
     */
    @JsonProperty("toolName")
    private String toolName;

    /**
     * 工具描述
     */
    @JsonProperty("toolDescription")
    private String toolDescription;

    /**
     * 输入参数schema
     */
    @JsonProperty("inputSchema")
    private Map<String, Object> inputSchema;

    /**
     * 输出参数schema
     */
    @JsonProperty("outputSchema")
    private Map<String, Object> outputSchema;

    /**
     * 输入槽位列表
     */
    @JsonProperty("inputSlots")
    private List<InputSlot> inputSlots;

    /**
     * 输出槽位推断列表
     */
    @JsonProperty("outputSlotsInferred")
    private List<OutputSlotInferred> outputSlotsInferred;

    /**
     * 所属技能名称（反引用）
     */
    private String skillName;

    /**
     * Embedding 向量（1024维）
     * 注意：此字段不会从 JSON 反序列化，仅在运行时生成
     */
    @JsonIgnore
    private double[] embedding;
}
