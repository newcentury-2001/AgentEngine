package com.agentengine.skill.embedding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 技能 Embedding 生成结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillEmbeddingResult {

    /**
     * 总技能数
     */
    private int totalSkills;

    /**
     * 成功生成数
     */
    private int successCount;

    /**
     * 失败数
     */
    private int failureCount;

    /**
     * 失败的技能列表
     */
    private List<String> failedSkills;

    /**
     * 处理耗时（毫秒）
     */
    private long processingTimeMs;

    /**
     * 消息
     */
    private String message;
}
