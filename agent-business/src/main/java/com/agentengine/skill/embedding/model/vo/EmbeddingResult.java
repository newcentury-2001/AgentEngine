package com.agentengine.skill.embedding.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Embedding 鐢熸垚缁撴灉
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingResult {

    /**
     * 鎬诲伐鍏锋暟
     */
    private int totalTools;

    /**
     * 鎴愬姛鐢熸垚鏁?     */
    private int successCount;

    /**
     * 澶辫触鏁?     */
    private int failureCount;

    /**
     * 澶辫触鐨勫伐鍏峰垪琛?     */
    private List<String> failedTools;

    /**
     * 澶勭悊鑰楁椂锛堟绉掞級
     */
    private long processingTimeMs;

    /**
     * 娑堟伅
     */
    private String message;
}


