package com.agentengine.skill.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 输入槽位实体类
 */
@Data
public class InputSlot {

    /**
     * 槽位键值
     */
    @JsonProperty("slotKey")
    private String slotKey;

    /**
     * 字段路径
     */
    @JsonProperty("fieldPath")
    private String fieldPath;

    /**
     * 字段类型
     */
    @JsonProperty("fieldType")
    private String fieldType;

    /**
     * 是否必填
     */
    @JsonProperty("required")
    private boolean required;
}
