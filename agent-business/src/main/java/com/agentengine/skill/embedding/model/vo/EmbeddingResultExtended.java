package com.agentengine.skill.embedding.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 鎵╁睍鐨?Embedding 鐢熸垚缁撴灉
 * 鍖呭惈鏁版嵁搴撴搷浣滅殑鐩稿叧缁熻
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingResultExtended {

    /**
     * 鎬诲伐鍏?鎶€鑳芥暟
     */
    private int totalItems;

    /**
     * Embedding 鐢熸垚鎴愬姛鏁?     */
    private int embeddingSuccessCount;

    /**
     * Embedding 鐢熸垚澶辫触鏁?     */
    private int embeddingFailureCount;

    /**
     * 澶辫触鐨勫伐鍏?鎶€鑳藉垪琛?     */
    private List<String> failedItems;

    /**
     * 鏁版嵁搴撳叆搴撴垚鍔熸暟
     */
    private int databaseSuccessCount;

    /**
     * 鏁版嵁搴撳叆搴撳け璐ユ暟
     */
    private int databaseFailureCount;

    /**
     * 鏁版嵁搴撳叆搴撳け璐ョ殑鍒楄〃
     */
    private List<String> databaseFailedItems;

    /**
     * Embedding 鐢熸垚鑰楁椂锛堟绉掞級
     */
    private long embeddingTimeMs;

    /**
     * 鏁版嵁搴撳叆搴撹€楁椂锛堟绉掞級
     */
    private long databaseTimeMs;

    /**
     * 鎬诲鐞嗚€楁椂锛堟绉掞級
     */
    private long totalTimeMs;

    /**
     * 娑堟伅
     */
    private String message;

    /**
     * 鎿嶄綔绫诲瀷
     */
    private String itemType; // "tool" 鎴?"skill"
}


