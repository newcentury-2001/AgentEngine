package com.agentengine.skill.embedding;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 技能 Embedding 生成请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillEmbeddingRequest {

    /**
     * 技能名称列表
     */
    private List<String> skillNames;

    /**
     * 是否强制重新生成（即使已有 embedding）
     */
    private boolean forceRegenerate = false;

    /**
     * 是否包含工具信息
     * 如果为 true，将技能包下的所有工具名称和描述也包含在 embedding prompt 中
     */
    private boolean includeTools = true;
}
