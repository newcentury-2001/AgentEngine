# 技能 Embedding API 文档

## 概述

本模块提供技能 embedding 向量生成功能，采用三层架构设计：

- **Controller 层**：处理 HTTP 请求，提供 REST API 接口
- **Service 层**：业务逻辑编排，协调各个组件
- **Resource 层**：调用第三方 API，使用线程池异步执行

## 技能 Embedding Prompt 构成

生成技能 embedding 时，会综合以下信息：

1. **技能基本信息**
   - 技能名称
   - 技能描述
   - 意图 (Intent)
   - 动作类型 (Action Type)

2. **技能标签**（如果存在）
   - 用于分类和搜索

3. **工具信息**（可选）
   - 技能包下所有工具的名称
   - 每个工具的描述

## API 接口

### 1. 生成指定技能的 Embedding

**端点：** `POST /api/skill-embedding/generate`

**请求体：**
```json
{
  "skillNames": [
    "recognition",
    "image-process",
    "search"
  ],
  "forceRegenerate": false,
  "includeTools": true
}
```

**参数说明：**
- `skillNames`：技能名称列表
- `forceRegenerate`：是否强制重新生成（即使已有 embedding）
- `includeTools`：是否包含技能包下的工具信息在 embedding prompt 中

**响应：**
```json
{
  "totalSkills": 3,
  "successCount": 3,
  "failureCount": 0,
  "failedSkills": [],
  "processingTimeMs": 4200,
  "message": "Skill embedding generation completed. Success: 3, Failure: 0"
}
```

### 2. 批量生成所有技能的 Embedding

**端点：** `POST /api/skill-embedding/generate-all?includeTools=true`

**参数：**
- `includeTools`（可选，默认 true）：是否包含工具信息

**响应：**
```json
{
  "totalSkills": 28,
  "successCount": 26,
  "failureCount": 2,
  "failedSkills": [
    "skill1",
    "skill2"
  ],
  "processingTimeMs": 35000,
  "message": "Skill embedding generation completed. Success: 26, Failure: 2"
}
```

### 3. 查询技能 Embedding 状态

**端点：** `GET /api/skill-embedding/status`

**参数：**
- `skillNames`：技能名称列表（可重复）

**示例：**
```
GET /api/skill-embedding/status?skillNames=recognition&skillNames=image-process&skillNames=search
```

**响应：**
```json
{
  "totalSkills": 3,
  "existingCount": 3,
  "missingCount": 0,
  "existingSkills": [
    "recognition",
    "image-process",
    "search"
  ],
  "missingSkills": []
}
```

## Embedding Prompt 示例

当 `includeTools=true` 时，生成的 prompt 示例：

```
Skill: recognition
Description: recognition技能，用于相关能力调用。
Intent: query
Action Type: read
Tags: 计算机视觉, 识别

Tools:
  - location_recognition: location_recognition：用于相关能力调用。
  - person_recognition: person_recognition：用于相关能力调用。
  - plant_recognition: plant_recognition：用于识别图片中的植物信息。
```

当 `includeTools=false` 时，生成的 prompt 示例：

```
Skill: recognition
Description: recognition技能，用于相关能力调用。
Intent: query
Action Type: read
Tags: 计算机视觉, 识别
```

## 前端调用示例

### JavaScript (Fetch API)

```javascript
// 生成指定技能的 embedding
async function generateSkillEmbeddings() {
  const response = await fetch('/api/skill-embedding/generate', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      skillNames: [
        'recognition',
        'image-process'
      ],
      forceRegenerate: false,
      includeTools: true
    })
  });
  return await response.json();
}

// 生成所有技能的 embedding
async function generateAllSkillEmbeddings(includeTools = true) {
  const response = await fetch(`/api/skill-embedding/generate-all?includeTools=${includeTools}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    }
  });
  return await response.json();
}

// 查询技能 embedding 状态
async function checkSkillEmbeddingStatus(skillNames) {
  const query = skillNames.map(name => `skillNames=${name}`).join('&');
  const response = await fetch(`/api/skill-embedding/status?${query}`);
  return await response.json();
}
```

### cURL

```bash
# 生成指定技能的 embedding
curl -X POST http://localhost:8080/api/skill-embedding/generate \
  -H "Content-Type: application/json" \
  -d '{
    "skillNames": ["recognition", "image-process"],
    "forceRegenerate": false,
    "includeTools": true
  }'

# 生成所有技能的 embedding
curl -X POST "http://localhost:8080/api/skill-embedding/generate-all?includeTools=true" \
  -H "Content-Type: application/json"

# 查询技能 embedding 状态
curl "http://localhost:8080/api/skill-embedding/status?skillNames=recognition&skillNames=image-process"
```

## 数据流架构

```
前端请求 List<String>
    ↓
Controller (SkillEmbeddingController)
    ↓ 接收 HTTP 请求，返回 CompletableFuture
Service (SkillEmbeddingOrchestrationService)
    ↓ 方法编排：解析 JSON → 过滤技能 → 构建 prompt → 生成 embedding
Resource (SkillEmbeddingResource)
    ↓ 调用第三方 API，使用 embeddingExecutor 线程池
第三方 Embedding API (智谱 AI embedding-3)
    ↓ 返回 1024 维向量
CompletableFuture<ResponseEntity<SkillEmbeddingResult>>
```

## 文件结构

```
agent-business/src/main/java/com/agentengine/skill/embedding/
├── SkillEmbeddingController.java            # Controller 层
├── SkillEmbeddingOrchestrationService.java  # Service 层
├── SkillEmbeddingResource.java             # Resource 层
├── SkillEmbeddingRequest.java             # 请求 DTO
├── SkillEmbeddingResult.java              # 响应 DTO
├── EmbeddingProperties.java              # 配置属性（共享）
├── EmbeddingExecutorConfig.java           # 线程池配置（共享）
└── agent-business/src/main/java/com/agentengine/skill/model/
    └── McpSkill.java                       # 添加了 tags 和 embedding 字段
```

## 使用场景

1. **前端选择技能生成 embedding**：用户在前端选择需要生成 embedding 的技能，可以控制是否包含工具信息
2. **批量初始化**：系统启动时需要更新所有技能 embedding 时调用
3. **查询状态**：前端需要查询某个技能是否已有 embedding 时

## 与工具 Embedding 的对比

| 特性 | 工具 Embedding | 技能 Embedding |
|------|---------------|---------------|
| API 路径 | `/api/embedding/*` | `/api/skill-embedding/*` |
| 生成维度 | 工具名称 + 描述 | 技能名称 + 描述 + 标签 + 工具列表 |
| 数据类型 | List<McpTool> | List<McpSkill> |
| 包含工具信息 | 不适用 | 可选参数控制 |

## 注意事项

1. 技能 embedding 会根据 `includeTools` 参数决定是否包含工具信息
2. 包含工具信息会让 prompt 更长，embedding 可能更准确但成本更高
3. 暂时不入库，仅回填到内存中的 McpSkill 对象
4. 失败的技能会记录在日志文件中，便于后续处理
5. 可以通过标签字段对技能进行分类和搜索
