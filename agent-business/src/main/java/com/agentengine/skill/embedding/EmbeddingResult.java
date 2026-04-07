package com.agentengine.skill.embedding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Embedding 生成结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingResult {

    /**
     * 总工具数
     */
    private int totalTools;

    /**
     * 成功生成数
     */
    private int successCount;

    /**
     * 失败数
     */
    private int failureCount;

    /**
     * 失败的工具列表
     */
    private List<String> failedTools;

    /**
     * 处理耗时（毫秒）
     */
    private long processingTimeMs;

    /**
     * 消息
     */
    private String message;
}
