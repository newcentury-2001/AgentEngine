package com.agentengine.skill.embedding;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Embedding 生成请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingRequest {

    /**
     * 工具名称列表
     * 格式：["skillName:toolName", "skillName:toolName"]
     */
    private List<String> toolNames;

    /**
     * 是否强制重新生成（即使已有 embedding）
     */
    private boolean forceRegenerate = false;
}
