package com.agentengine.skill.embedding.model.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 鎶€鑳?Embedding 鐢熸垚璇锋眰
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillEmbeddingRequest {

    /**
     * 鎶€鑳藉悕绉板垪琛?     */
    private List<String> skillNames;

    /**
     * 鏄惁寮哄埗閲嶆柊鐢熸垚锛堝嵆浣垮凡鏈?embedding锛?     */
    private boolean forceRegenerate = false;

    /**
     * 鏄惁鍖呭惈宸ュ叿淇℃伅
     * 濡傛灉涓?true锛屽皢鎶€鑳藉寘涓嬬殑鎵€鏈夊伐鍏峰悕绉板拰鎻忚堪涔熷寘鍚湪 embedding prompt 涓?     */
    private boolean includeTools = true;
}


