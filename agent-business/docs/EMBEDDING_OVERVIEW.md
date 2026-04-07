# Embedding 系统总览

## 概述

本系统提供工具和技能两个层级的 embedding 向量生成功能，均采用三层架构设计，支持异步并发处理。

## 两个 Controller 链路

### 1. 工具 Embedding 链路

**端点前缀：** `/api/embedding`

**API 接口：**
- `POST /api/embedding/generate` - 生成指定工具的 embedding
- `POST /api/embedding/generate-all` - 批量生成所有工具的 embedding
- `GET /api/embedding/status` - 查询工具 embedding 状态

**特点：**
- 基于 `skillName:toolName` 生成 embedding
- prompt 包含：工具名称 + 工具描述
- 输出：1024 维向量
- 返回类型：`CompletableFuture<ResponseEntity<EmbeddingResult>>`

### 2. 技能 Embedding 链路

**端点前缀：** `/api/skill-embedding`

**API 接口：**
- `POST /api/skill-embedding/generate` - 生成指定技能的 embedding
- `POST /api/skill-embedding/generate-all` - 批量生成所有技能的 embedding
- `GET /api/skill-embedding/status` - 查询技能 embedding 状态

**特点：**
- 基于 `skillName` 生成 embedding
- prompt 包含：技能名称 + 技能描述 + 标签 + 工具列表（可选）
- 输出：1024 维向量
- 返回类型：`CompletableFuture<ResponseEntity<SkillEmbeddingResult>>`

## 架构设计

### 三层架构

```
前端请求
    ↓
Controller 层
    ├─ EmbeddingController (工具)
    └─ SkillEmbeddingController (技能)
    ↓
Service 层
    ├─ EmbeddingOrchestrationService (工具编排)
    └─ SkillEmbeddingOrchestrationService (技能编排)
    ↓
Resource 层
    ├─ EmbeddingResource (工具 API 调用)
    └─ SkillEmbeddingResource (技能 API 调用)
    ↓
第三方 API (智谱 AI embedding-3)
    ↓
返回 CompletableFuture
```

### 共享组件

1. **配置和线程池**
   - `EmbeddingProperties` - Embedding 配置属性
   - `EmbeddingExecutorConfig` - 线程池和 HttpClient 配置
   - 线程池：embeddingExecutor（从 IOC 容器注入）

2. **数据模型**
   - `McpTool` - 工具实体（添加了 embedding 字段）
   - `McpSkill` - 技能实体（添加了 tags 和 embedding 字段）

## API 快速参考

### 工具 Embedding

```bash
# 生成指定工具
curl -X POST http://localhost:8080/api/embedding/generate \
  -H "Content-Type: application/json" \
  -d '{"toolNames": ["recognition:location_recognition"]}'

# 生成所有工具
curl -X POST http://localhost:8080/api/embedding/generate-all

# 查询状态
curl "http://localhost:8080/api/embedding/status?toolNames=recognition:location_recognition"
```

### 技能 Embedding

```bash
# 生成指定技能
curl -X POST http://localhost:8080/api/skill-embedding/generate \
  -H "Content-Type: application/json" \
  -d '{"skillNames": ["recognition"], "includeTools": true}'

# 生成所有技能
curl -X POST "http://localhost:8080/api/skill-embedding/generate-all?includeTools=true"

# 查询状态
curl "http://localhost:8080/api/skill-embedding/status?skillNames=recognition"
```

## 文件结构

```
agent-business/
├── src/main/java/com/agentengine/skill/
│   ├── embedding/
│   │   ├── EmbeddingController.java           # 工具 Controller
│   │   ├── EmbeddingOrchestrationService.java # 工具 Service
│   │   ├── EmbeddingResource.java             # 工具 Resource
│   │   ├── EmbeddingProperties.java          # 配置属性
│   │   ├── EmbeddingExecutorConfig.java       # 线程池配置
│   │   ├── EmbeddingRequest.java            # 工具请求 DTO
│   │   ├── EmbeddingResult.java             # 工具响应 DTO
│   │   ├── SkillEmbeddingController.java       # 技能 Controller
│   │   ├── SkillEmbeddingOrchestrationService.java # 技能 Service
│   │   ├── SkillEmbeddingResource.java       # 技能 Resource
│   │   ├── SkillEmbeddingRequest.java       # 技能请求 DTO
│   │   ├── SkillEmbeddingResult.java        # 技能响应 DTO
│   │   └── EmbeddingApiDemo.java          # API 使用演示
│   ├── model/
│   │   ├── McpTool.java                   # 工具实体（含 embedding）
│   │   └── McpSkill.java                   # 技能实体（含 embedding）
│   └── parser/
│       └── McpJsonParser.java            # JSON 解析工具
└── docs/
    ├── EMBEDDING_API.md                  # 工具 Embedding 文档
    └── SKILL_EMBEDDING_API.md           # 技能 Embedding 文档
```

## 配置示例

```yaml
embedding:
  enabled: true
  base-url: https://open.bigmodel.cn/api/paas/v4
  api-key: ${zhipukey:}
  model: embedding-3
  connect-timeout-ms: 1000
  request-timeout-ms: 15000
  max-retries: 2
  thread-pool-core-size: 6
  thread-pool-max-size: 12
  qps-limit: 8
  failure-rate-threshold: 0.5
  log-file-path: logs/embedding_results.log
```

## 使用场景

### 场景 1：前端选择工具生成 embedding

```javascript
// 用户选择多个工具
const selectedTools = [
  'recognition:location_recognition',
  'image-process:crop',
  'search:image_search_with_image'
];

// 调用工具 embedding API
const result = await fetch('/api/embedding/generate', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    toolNames: selectedTools,
    forceRegenerate: false
  })
});
```

### 场景 2：系统启动时批量生成所有技能 embedding

```javascript
// 系统启动时调用
const result = await fetch('/api/skill-embedding/generate-all?includeTools=true', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' }
});

console.log(`Generated ${result.successCount}/${result.totalSkills} skills`);
```

### 场景 3：混合使用工具和技能 embedding

```javascript
// 为部分工具生成 embedding
await fetch('/api/embedding/generate', {
  method: 'POST',
  body: JSON.stringify({ toolNames: ['skill1:tool1'] })
});

// 为部分技能生成 embedding
await fetch('/api/skill-embedding/generate', {
  method: 'POST',
  body: JSON.stringify({
    skillNames: ['skill2'],
    includeTools: true
  })
});
```

## 性能特点

### 并发处理
- 使用 `CompletableFuture.allOf()` 实现异步并发
- 线程池从 IOC 容器注入
- 单个失败不影响整体

### 限流和熔断
- QPS 限制：8
- 熔断阈值：0.5
- 快速失败，记录失败信息

### 资源管理
- HTTP/2 连接池
- 连接超时：1s
- 请求超时：15s

## 注意事项

1. **工具名称格式**：必须为 `skillName:toolName`
2. **技能 prompt 构建**：会根据 `includeTools` 参数决定是否包含工具信息
3. **暂不入库**：仅回填到内存对象
4. **日志记录**：失败信息记录在日志文件中
5. **异步处理**：所有接口返回 `CompletableFuture`

## 相关文档

- [工具 Embedding API 文档](./EMBEDDING_API.md)
- [技能 Embedding API 文档](./SKILL_EMBEDDING_API.md)
