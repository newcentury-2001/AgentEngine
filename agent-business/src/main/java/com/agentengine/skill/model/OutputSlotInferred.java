package com.agentengine.skill.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 输出槽位推断实体类
 */
@Data
public class OutputSlotInferred {

    /**
     * 槽位键值
     */
    @JsonProperty("slotKey")
    private String slotKey;

    /**
     * 置信度
     */
    @JsonProperty("confidence")
    private String confidence;
}
