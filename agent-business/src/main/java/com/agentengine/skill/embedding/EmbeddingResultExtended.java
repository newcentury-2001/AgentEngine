package com.agentengine.skill.embedding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 扩展的 Embedding 生成结果
 * 包含数据库操作的相关统计
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingResultExtended {

    /**
     * 总工具/技能数
     */
    private int totalItems;

    /**
     * Embedding 生成成功数
     */
    private int embeddingSuccessCount;

    /**
     * Embedding 生成失败数
     */
    private int embeddingFailureCount;

    /**
     * 失败的工具/技能列表
     */
    private List<String> failedItems;

    /**
     * 数据库入库成功数
     */
    private int databaseSuccessCount;

    /**
     * 数据库入库失败数
     */
    private int databaseFailureCount;

    /**
     * 数据库入库失败的列表
     */
    private List<String> databaseFailedItems;

    /**
     * Embedding 生成耗时（毫秒）
     */
    private long embeddingTimeMs;

    /**
     * 数据库入库耗时（毫秒）
     */
    private long databaseTimeMs;

    /**
     * 总处理耗时（毫秒）
     */
    private long totalTimeMs;

    /**
     * 消息
     */
    private String message;

    /**
     * 操作类型
     */
    private String itemType; // "tool" 或 "skill"
}
