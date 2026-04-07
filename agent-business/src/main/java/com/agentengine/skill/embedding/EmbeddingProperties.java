package com.agentengine.skill.embedding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Embedding 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {

    /**
     * 是否启用 embedding 生成
     */
    private boolean enabled = true;

    /**
     * API 基础 URL
     */
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";

    /**
     * API Key
     */
    private String apiKey = "";

    /**
     * Embedding 模型名称
     */
    private String model = "embedding-3";

    /**
     * 连接超时时间（毫秒）
     */
    private int connectTimeoutMs = 1000;

    /**
     * 请求超时时间（毫秒）
     */
    private int requestTimeoutMs = 15000;

    /**
     * 最大重试次数
     */
    private int maxRetries = 2;

    /**
     * 线程池核心大小
     */
    private int threadPoolCoreSize = 6;

    /**
     * 线程池最大大小
     */
    private int threadPoolMaxSize = 12;

    /**
     * QPS 限制
     */
    private int qpsLimit = 8;

    /**
     * 熔断失败率阈值
     */
    private double failureRateThreshold = 0.5;

    /**
     * 日志文件路径
     */
    private String logFilePath = "logs/embedding_results.log";
}
