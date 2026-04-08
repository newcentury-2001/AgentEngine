# Redis 设计（单 Session 覆盖式运行态）

## 1. 目标与约束

- 目标：支持单个用户单 Session 下的任务运行态管理（`taskId` / `traceId` / 状态机），并配合槽位存储与工具热度统计。
- 约束：同一 `userId` 同时只有 1 个活跃任务；新请求可覆盖旧运行态。

## 2. Key 设计

### 2.1 用户运行态（覆盖式）

- Key：`user:{userId}:runtime`
- Type：`HASH`
- Fields：
  - `taskId`：当前任务 ID（最新）
  - `traceId`：当前请求链路 ID（最新）
  - `status`：状态机状态（见第 4 节）
  - `updatedAt`：更新时间（毫秒时间戳）
- TTL：建议 `1800s`（30 分钟）
- 策略：每次访问或状态流转都续期；新 `taskId/traceId` 到来直接覆盖。

### 2.2 用户槽位（小 key + TTL）

- Key：`slot:{userId}:{slotName}`
- Type：`STRING`
- Value：`slotValue`
- TTL：按业务会话窗口设置（建议与运行态同级或略长）
- 设计目的：避免大 key，按槽位独立过期。

### 2.3 工具每日热度

- 保持你现有方案：定时任务 + `HSCAN` 分批 + Lua 脚本聚合。
- 建议命名（可选）：`tool:heat:{yyyyMMdd}`

## 3. 核心读写流程

### 3.1 开始一轮请求

1. 读取/生成 `taskId`（前端有则复用，无则后端生成并回传）。
2. 生成本次 `traceId`（请求级）。
3. 写入 `user:{userId}:runtime`：
   - `taskId=...`
   - `traceId=...`
   - `status=thinking`
   - `updatedAt=now`
4. `EXPIRE user:{userId}:runtime 1800`

### 3.2 LLM 编排每个阶段切换

- 阶段切换时更新 `status` 与 `updatedAt`，并续期：
  - `thinking`
  - `tool_calling`
  - `slot_asking`
  - `answering`
  - `done`
  - `paused`
  - `stopped`

### 3.3 暂停/停止

1. 前端发暂停请求（携带 `userId`）。
2. 后端将 `user:{userId}:runtime.status` 更新为 `paused` 或 `stopped`，并续期。
3. 每轮 LLM/工具调用前先检查该状态：
   - `paused/stopped`：立即中断后续调用。

## 4. 状态机定义

- `thinking`：模型思考/规划阶段
- `tool_calling`：调用工具阶段
- `slot_asking`：缺槽位反问用户阶段
- `answering`：总结回答阶段
- `done`：本轮完成
- `paused`：用户暂停
- `stopped`：用户终止

状态流转示例：

`thinking -> tool_calling -> (slot_asking <-> thinking) -> answering -> done`

任意状态可转 `paused` / `stopped`。

## 5. 并发与覆盖语义

- 当前模型是“单 Session 单活跃任务”，允许覆盖：
  - 新请求到来时直接覆盖 `taskId/traceId/status`。
- 适用前提：同一用户不会并发多窗口同时运行不同任务。
- 若未来支持并发任务，需升级为 `task:{taskId}` 独立模型。

## 6. 推荐命令示例

```redis
HSET user:{userId}:runtime taskId {taskId} traceId {traceId} status thinking updatedAt {ts}
EXPIRE user:{userId}:runtime 1800

HSET user:{userId}:runtime status tool_calling updatedAt {ts}
EXPIRE user:{userId}:runtime 1800

HGET user:{userId}:runtime status

SET slot:{userId}:destination "杭州" EX 1800
GET slot:{userId}:destination
```

## 7. 观测建议

- 日志统一输出：`userId, taskId, traceId, status`。
- 接口响应头保留：
  - `X-Task-Id`
  - `X-Trace-Id`
- 异步线程（CompletableFuture/线程池任务）通过 `TaskContext` 透传 `taskId/traceId` 到 MDC。

## 8. TTL 建议值

- `user:{userId}:runtime`：`1800s`
- `slot:{userId}:{slotName}`：`1800s`（或按槽位语义微调）
- 热度统计：按你现有定时策略保留

---

本设计为“覆盖式轻量运行态”，适合当前单 Session 场景，优先保证简洁、低维护成本与快速暂停控制。
