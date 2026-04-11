package com.agentengine.skill.embedding.model.pojo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Embedding 閰嶇疆灞炴€? */
@Data
@Component
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {

    /**
     * 鏄惁鍚敤 embedding 鐢熸垚
     */
    private boolean enabled = true;

    /**
     * API 鍩虹 URL
     */
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";

    /**
     * API Key
     */
    private String apiKey = "";

    /**
     * Embedding 妯″瀷鍚嶇О
     */
    private String model = "embedding-3";

    /**
     * 杩炴帴瓒呮椂鏃堕棿锛堟绉掞級
     */
    private int connectTimeoutMs = 1000;

    /**
     * 璇锋眰瓒呮椂鏃堕棿锛堟绉掞級
     */
    private int requestTimeoutMs = 15000;

    /**
     * 鏈€澶ч噸璇曟鏁?     */
    private int maxRetries = 2;

    /**
     * 绾跨▼姹犳牳蹇冨ぇ灏?     */
    private int threadPoolCoreSize = 6;

    /**
     * 绾跨▼姹犳渶澶уぇ灏?     */
    private int threadPoolMaxSize = 12;

    /**
     * QPS 闄愬埗
     */
    private int qpsLimit = 8;

    /**
     * 鐔旀柇澶辫触鐜囬槇鍊?     */
    private double failureRateThreshold = 0.5;

    /**
     * 鏃ュ織鏂囦欢璺緞
     */
    private String logFilePath = "logs/embedding_results.log";

    /**
     * 提交到 embedding 线程池时，若被拒绝，最多重试次数（含首次）
     */
    private int submitRetryMaxAttempts = 3;

    /**
     * 提交被拒绝后的重试等待毫秒（在后台工作线程 sleep，不阻塞 Tomcat 线程）
     */
    private long submitRetrySleepMs = 200;
}


