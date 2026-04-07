# Embedding API 文档

## 概述

本模块提供工具 embedding 向量生成功能，采用三层架构设计：

- **Controller 层**：处理 HTTP 请求，提供 REST API 接口
- **Service 层**：业务逻辑编排，协调各个组件
- **Resource 层**：调用第三方 API，使用线程池异步执行

## 架构

```
前端请求
    ↓
Controller (EmbeddingController)
    ↓
Service (EmbeddingOrchestrationService)
    ↓
Resource (EmbeddingResource) ← 线程池 (IOC 容器)
    ↓
第三方 Embedding API (智谱 AI)
    ↓
返回 CompletableFuture<ResponseEntity<EmbeddingResult>>
```

## API 接口

### 1. 生成指定工具的 Embedding

**端点：** `POST /api/embedding/generate`

**请求体：**
```json
{
  "toolNames": [
    "recognition:location_recognition",
    "recognition:person_recognition",
    "image-process:crop"
  ],
  "forceRegenerate": false
}
```

**参数说明：**
- `toolNames`：工具名称列表，格式为 `skillName:toolName`
- `forceRegenerate`：是否强制重新生成（即使已有 embedding）

**响应：**
```json
{
  "totalTools": 3,
  "successCount": 3,
  "failureCount": 0,
  "failedTools": [],
  "processingTimeMs": 3500,
  "message": "Embedding generation completed. Success: 3, Failure: 0"
}
```

### 2. 批量生成所有工具的 Embedding

**端点：** `POST /api/embedding/generate-all`

**请求体：** 无

**响应：**
```json
{
  "totalTools": 88,
  "successCount": 85,
  "failureCount": 3,
  "failedTools": [
    "skill1:tool1",
    "skill2:tool2",
    "skill3:tool3"
  ],
  "processingTimeMs": 12000,
  "message": "Embedding generation completed. Success: 85, Failure: 3"
}
```

### 3. 查询 Embedding 状态

**端点：** `GET /api/embedding/status`

**参数：**
- `toolNames`：工具名称列表（可重复）

**示例：**
```
GET /api/embedding/status?toolNames=recognition:location_recognition&toolNames=recognition:person_recognition
```

**响应：**
```json
{
  "totalTools": 2,
  "existingCount": 2,
  "missingCount": 0,
  "existingTools": [
    "recognition:location_recognition",
    "recognition:person_recognition"
  ],
  "missingTools": []
}
```

## 前端调用示例

### JavaScript (Fetch API)

```javascript
// 生成指定工具的 Embedding
async function generateToolEmbeddings() {
  const response = await fetch('/api/embedding/generate', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      toolNames: [
        'recognition:location_recognition',
        'recognition:person_recognition'
      ],
      forceRegenerate: false
    })
  });
  return await response.json();
}

// 生成所有工具的 Embedding
async function generateAllEmbeddings() {
  const response = await fetch('/api/embedding/generate-all', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    }
  });
  return await response.json();
}

// 查询 Embedding 状态
async function checkEmbeddingStatus(toolNames) {
  const query = toolNames.map(name => `toolNames=${name}`).join('&');
  const response = await fetch(`/api/embedding/status?${query}`);
  return await response.json();
}
```

### cURL

```bash
# 生成指定工具的 Embedding
curl -X POST http://localhost:8080/api/embedding/generate \
  -H "Content-Type: application/json" \
  -d '{
    "toolNames": ["recognition:location_recognition"],
    "forceRegenerate": false
  }'

# 生成所有工具的 Embedding
curl -X POST http://localhost:8080/api/embedding/generate-all \
  -H "Content-Type: application/json"

# 查询 Embedding 状态
curl "http://localhost:8080/api/embedding/status?toolNames=recognition:location_recognition&toolNames=recognition:person_recognition"
```

## 配置

在 `application.yml` 中配置：

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

## 特性

### 1. 异步并发处理
- 使用 `CompletableFuture.allOf()` 等待所有任务完成
- 线程池从 IOC 容器获取
- 单个失败不影响整体

### 2. QPS 限流和熔断
- 使用 `FlowControlExecutor` 提供 QPS 限制和熔断器
- QPS 限制：8
- 熔断阈值：0.5

### 3. 快速失败 + 日志记录
- 限流快速失败，记录失败的工具名称
- 写入日志文件 `logs/embedding_results.log`
- 包含成功数、失败数、失败工具列表

### 4. 数据流完整
- 前端 → Controller → Service → Resource → 第三方 API → 前端
- 每层职责清晰，易于测试和维护
- 返回 `CompletableFuture` 支持异步处理

## 文件结构

```
agent-business/src/main/java/com/agentengine/skill/embedding/
├── EmbeddingController.java          # Controller 层
├── EmbeddingOrchestrationService.java # Service 层
├── EmbeddingResource.java           # Resource 层
├── EmbeddingProperties.java          # 配置属性
├── EmbeddingExecutorConfig.java     # 线程池配置
├── EmbeddingRequest.java           # 请求 DTO
├── EmbeddingResult.java            # 响应 DTO
├── EmbeddingService.java           # 已有服务（保留）
├── ToolEmbeddingGenerator.java     # 已有工具类（保留）
└── EmbeddingApiDemo.java          # API 使用演示
```

## 使用场景

1. **前端选择工具生成 embedding**：用户在前端选择需要生成 embedding 的工具，调用 `/api/embedding/generate` 接口

2. **批量初始化**：系统启动时或需要更新所有 embedding 时，调用 `/api/embedding/generate-all` 接口

3. **查询状态**：前端需要查询某个工具是否已有 embedding 时，调用 `/api/embedding/status` 接口

## 注意事项

1. 工具名称格式必须为 `skillName:toolName`
2. 暂时不入库，仅回填到内存中的 McpTool 对象
3. 失败的工具会记录在日志文件中，便于后续处理
4. QPS 限制可能导致部分工具生成失败，这是正常现象
