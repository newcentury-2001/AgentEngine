package com.agentengine.skill.embedding.model.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Embedding 鐢熸垚璇锋眰
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingRequest {

    /**
     * 宸ュ叿鍚嶇О鍒楄〃
     * 鏍煎紡锛歔"skillName:toolName", "skillName:toolName"]
     */
    private List<String> toolNames;

    /**
     * 鏄惁寮哄埗閲嶆柊鐢熸垚锛堝嵆浣垮凡鏈?embedding锛?     */
    private boolean forceRegenerate = false;
}


