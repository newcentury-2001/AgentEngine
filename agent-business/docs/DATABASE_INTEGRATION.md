# Embedding 数据库集成文档

## 概述

本系统实现了 embedding 生成后的 PostgreSQL 数据库入库功能，采用职责分离的设计：

- **Service 层**：编排 embedding 生成、数据库入库、日志记录三个操作
- **Resource 层**：负责调用第三方 embedding API 和生成 embedding
- **Repository 层**：负责数据库批量入库操作
- **Log Service 层**：负责写入日志文件

## 架构设计

### 数据流

```
前端请求 List<String>
    ↓
Controller (接收请求，返回 CompletableFuture)
    ↓
Service (编排：生成 → 入库 → 日志)
    ↓
Resource (生成 embedding，使用 embeddingExecutor)
    ↓
CompletableFuture (embedding 生成完成)
    ↓
并行执行：
    ├─ CompletableFuture → PostgreSQL 入库 (使用 pgIoExecutor)
    └─ CompletableFuture → 写日志文件 (使用 pgIoExecutor)
    ↓
CompletableFuture.allOf() (等待两个都完成)
    ↓
统一返回结果 (包含 embedding 和数据库统计)
```

## 线程池配置

### Embedding 线程池
- **用途**：调用 embedding API，生成 1024 维向量
- **配置**：
  ```yaml
  embedding:
    thread-pool-core-size: 6
    thread-pool-max-size: 12
    qps-limit: 8
  ```
- **QPS 限制**：8
- **熔断阈值**：0.5

### PostgreSQL 线程池
- **用途**：批量入库操作，使用 JdbcTemplate
- **配置**：
  ```yaml
  thread-pool:
    pg-io:
      core-cpu-ratio: 0.75
      daemon: false
      reject-policy: degrade
  ```
- **计算**：核心线程数 = CPU 核心数 × 0.75
- **拒绝策略**：degrade (降级拒绝)

## 数据库操作

### 工具入库
**表**：`mcp_tool_vector`
```sql
INSERT INTO mcp_tool_vector (skill_name, tool_name, normalized_vector, updated_at)
VALUES (?, ?, ?, ?, NOW())
ON CONFLICT (skill_name, tool_name)
DO UPDATE SET
    normalized_vector = EXCLUDED.normalized_vector,
    updated_at = NOW()
```

### 技能入库
**表**：`skill_vector_snapshot`
```sql
INSERT INTO skill_vector_snapshot (skill_name, skill_description, skill_vector, updated_at)
VALUES (?, ?, ?, ?, NOW())
ON CONFLICT (skill_name)
DO UPDATE SET
    skill_description = EXCLUDED.skill_description,
    skill_vector = EXCLUDED.skill_vector,
    updated_at = NOW()
```

### 批量优化
- 使用 `JdbcTemplate.batchUpdate()` 批量入库
- 每次批量操作包含多个 INSERT/UPDATE 语句
- 使用 ON CONFLICT 处理重复数据（更新而非插入）

## 返回结果

### EmbeddingResultExtended 结构

```java
public class EmbeddingResultExtended {
    // Embedding 生成统计
    private int embeddingSuccessCount;   // embedding 生成成功数
    private int embeddingFailureCount;   // embedding 生成失败数
    private List<String> failedItems;     // embedding 失败的工具/技能列表

    // 数据库入库统计
    private int databaseSuccessCount;     // 入库成功数
    private int databaseFailureCount;      // 入库失败数
    private List<String> databaseFailedItems; // 入库失败的工具/技能列表

    // 耗时统计
    private long embeddingTimeMs;        // embedding 生成耗时
    private long databaseTimeMs;         // 数据库入库耗时
    private long totalTimeMs;            // 总耗时

    private String itemType;               // "tool" 或 "skill"
}
```

## API 响应示例

### 工具 Embedding 响应

```json
{
  "totalItems": 10,
  "embeddingSuccessCount": 9,
  "embeddingFailureCount": 1,
  "failedItems": ["skill1:tool1"],
  "databaseSuccessCount": 8,
  "databaseFailureCount": 1,
  "databaseFailedItems": ["skill1:tool1"],
  "embeddingTimeMs": 3500,
  "databaseTimeMs": 4200,
  "totalTimeMs": 7700,
  "message": "Tool embedding completed. Embedding: 9/10, Database: 8/9",
  "itemType": "tool"
}
```

### 技能 Embedding 响应

```json
{
  "totalItems": 3,
  "embeddingSuccessCount": 3,
  "embeddingFailureCount": 0,
  "failedItems": [],
  "databaseSuccessCount": 2,
  "databaseFailureCount": 1,
  "databaseFailedItems": ["skill2"],
  "embeddingTimeMs": 4200,
  "databaseTimeMs": 3800,
  "totalTimeMs": 8000,
  "message": "Skill embedding completed. Embedding: 3/3, Database: 2/3",
  "itemType": "skill"
}
```

## 文件结构

```
agent-business/src/main/java/com/agentengine/skill/embedding/
├── Controller 层
│   ├── EmbeddingController.java              # 工具 Controller
│   └── SkillEmbeddingController.java       # 技能 Controller
├── Service 层
│   ├── EmbeddingOrchestrationService.java # 工具编排服务
│   └── SkillEmbeddingOrchestrationService.java # 技能编排服务
├── Resource 层
│   ├── EmbeddingResource.java             # 工具 Resource
│   └── SkillEmbeddingResource.java         # 技能 Resource
├── Repository 层
│   └── EmbeddingDbRepository.java         # 数据库操作
├── Log Service 层
│   └── EmbeddingLogFileService.java        # 日志文件服务
├── 配置层
│   ├── EmbeddingProperties.java            # Embedding 配置
│   ├── EmbeddingExecutorConfig.java         # Embedding 线程池配置
│   ├── PgExecutorConfigProperties.java       # PG 线程池属性
│   └── PgConfig.java                   # PG 线程池 Bean
└── DTO 类
    ├── EmbeddingRequest.java               # 工具请求 DTO
    ├── EmbeddingResult.java                # 工具响应 DTO（已弃用）
    ├── EmbeddingResultExtended.java        # 扩展的响应 DTO
    ├── SkillEmbeddingRequest.java           # 技能请求 DTO
    └── SkillEmbeddingResult.java           # 技能响应 DTO（已弃用）
```

## 使用方式

### 前端调用

```javascript
// 工具 embedding（包含入库）
const result = await fetch('/api/embedding/generate', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    toolNames: ['skill1:tool1', 'skill2:tool2'],
    forceRegenerate: false
  })
});

console.log('Embedding:', result.embeddingSuccessCount, '/', result.totalItems);
console.log('Database:', result.databaseSuccessCount, '/', result.embeddingSuccessCount);
console.log('Failed:', result.databaseFailureCount);

// 技能 embedding（包含入库）
const skillResult = await fetch('/api/skill-embedding/generate', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    skillNames: ['skill1', 'skill2'],
    forceRegenerate: false,
    includeTools: true
  })
});
```

## 注意事项

1. **职责分离**：Controller 只负责接收请求和返回结果，不直接调用入库
2. **线程池分离**：Embedding 使用 embeddingExecutor，入库使用 pgIoExecutor
3. **异步并行**：embedding 生成完成后，入库和日志并行执行
4. **结果扩展**：返回 `EmbeddingResultExtended`，包含完整的统计信息
5. **日志记录**：失败的工具/技能会记录到日志文件 `logs/embedding_results.log`
6. **数据库约束**：使用 ON CONFLICT 处理重复，支持更新操作
7. **向量格式**：double[] 转换为 JSON 字符串存储在 PostgreSQL JSONB 字段

## 性能优化

1. **批量入库**：使用 JdbcTemplate.batchUpdate() 减少 DB 连接次数
2. **并行处理**：入库和日志使用同一个线程池并行执行
3. **复用连接**：使用 PostgreSQL 连接池，避免频繁创建连接
4. **异步非阻塞**：使用 CompletableFuture.allOf() 确保所有操作完成后才返回
