# Assistant 主链路 Spec（两态版）

## 1. 目标与范围

### 1.1 目标
- 将助手主链路稳定在**两态模型**：`IDLE` / `ACTIVE`。
- 形成可持续演进的“按图施工”基线：接口、状态机、Redis、阶段路由、LLM 约束、错误处理。
- 保持当前代码可运行，并为后续“真实工具执行/回复生成”预留清晰挂点。

### 1.2 范围（本 spec 覆盖）
- 模块：`agent-business`
- 主入口：`/api/assistant/state/execute`
- 关键类：
  - `AssistantStateController`
  - `AssistantAgentOrchestrationService`
  - `AssistantStateMachineService`
  - `AssistantInferenceService`
  - `IntentRecognitionStageInputService`
  - `SlotClarificationStageInputService`
  - `ToolExecutionStageInputService`
  - `AssistantDialogueService`
  - `AssistantEntityMemoryService`

### 1.3 非范围
- 不包含 RocketMQ 异步链路（当前 assistant 主链路为同步 HTTP 处理）。
- 不包含真实 MCP 工具调用执行器（当前 `TOOL_EXECUTION` 仍是“槽位补齐推理”逻辑）。
- 不包含前端页面交互细节与 UI 状态管理。

## 2. 总体架构

### 2.1 主流程概览
1. Controller 读取或创建会话状态。
2. Controller 做一次逻辑态判定（IDLE/ACTIVE），必要时先落状态机。
3. Orchestration 根据当前态 + 请求内容选择 Stage。
4. Stage 调用 Inference，回填 `needTool/answerReady/toolName/missingSlots/errorMessage`。
5. Orchestration 根据回填结果决定下一逻辑态并落库到 Redis。
6. 返回最新 `AssistantUserState`。

### 2.2 两态定义
- `IDLE`：意图待识别或需要重新识别（包括错误回退）。
- `ACTIVE`：意图已明确，处于“补槽/执行”过程中。

## 3. 接口规范

## 3.1 POST `/api/assistant/state/execute`
请求体（核心字段）：
- `userId`：必填
- `taskId`：首轮必填；后续可仅传 `userId` 命中会话
- `traceId`：可选
- `message`：用户输入
- `needTool/answerReady/toolName/missingSlots/errorMessage`：链路中间字段（通常由后端回填）

返回：`AssistantUserState`
- `userId`
- `taskId`
- `traceId`
- `state` (`IDLE|ACTIVE`)
- `createdAtEpochMs`
- `updatedAtEpochMs`
- `lastMessage`
- `missingSlots`
- `errorMessage`

错误码：
- `400`：参数错误（如缺 `userId`、首轮缺 `taskId`）
- `409`：状态机冲突（非法状态迁移）

## 3.2 GET `/api/assistant/state/task?taskId=...`
- 查询 task 维度状态。

## 3.3 GET `/api/assistant/state/user?userId=...`
- 查询 user 维度当前绑定 task 状态。

## 4. 状态机规范

## 4.1 状态迁移
允许迁移：
- `IDLE -> ACTIVE`
- `ACTIVE -> IDLE`
- `IDLE -> IDLE`
- `ACTIVE -> ACTIVE`

禁止：任何非 `IDLE/ACTIVE` 枚举值。

## 4.2 两态主判定（Intent 驱动）
主规则：
1. 从 Redis 会话上下文读取 `intent`。
2. `intent == null` 或空字符串 => `IDLE`
3. `intent != null` 且非空 => `ACTIVE`

说明：
- 两态判定以 `intent` 是否存在为第一原则，不再以 `needTool/answerReady/missingSlots` 作为主入口判定条件。
- `needTool/answerReady/missingSlots` 用于 ACTIVE 态内部流程推进（补槽、执行、收敛），不负责定义会话主态。

## 4.3 覆盖规则（高优先级）
在主判定之外，以下事件可强制改写状态：
1. 意图切换信号成立：清空当前 `intent`，并强制回到 `IDLE`。
2. 会话完成或超时重置：回到 `IDLE`（按策略清空或降级 `intent`）。
3. 严重异常且无法继续当前意图：回到 `IDLE`，等待下一轮重新判定。

## 5. Stage 路由规范

内部阶段枚举：
- `INTENT_RECOGNITION`
- `SLOT_CLARIFICATION`
- `TOOL_EXECUTION`

路由规则：
1. 当前逻辑态 `IDLE` => `INTENT_RECOGNITION`
2. 当前逻辑态 `ACTIVE` 且 `missingSlots`（请求或状态中）非空 => `SLOT_CLARIFICATION`
3. 否则 => `TOOL_EXECUTION`

备注：当前 `TOOL_EXECUTION` 与 `SLOT_CLARIFICATION` 都调用 `inferForSlotFill`，后续应拆分为“补槽”和“执行器编排”两条能力。

## 5.1 IDLE 阶段意图识别流水线（施工基线）
当主态为 `IDLE` 时，按以下步骤确定意图：
1. 组装短期记忆上下文：`历史 2 轮 + 当前 1 轮`（共 3 轮）。
2. 对这 3 轮文本生成 query embedding。
3. 在技能向量库按相似度召回技能，先过滤低相似度候选，再取 TopK（最多 10 个）。
   - 过滤阈值：`minSimThreshold`（配置项：`agent.assistant.intent.min-sim-threshold`，默认 `0.25`）。
4. 意图打分不做求和投票，改为“最强技能定分”：
   - `score(intentTag) = max(similarity(skill))`
5. 得到结果后计算：
   - `top1Score`、`top2Score`
   - `confidence = top1Score`
6. 判定：
   - 若 `confidence < confidenceThreshold`，进入 `LOW_CONFIDENCE` 澄清分支，不写入 intent，保持 `IDLE`。
   - 若 `(top1Score - top2Score) < deltaThreshold`，进入 `AMBIGUOUS_TOP2` 澄清分支，不写入 intent，保持 `IDLE`。
   - 否则将 `top1Intent` 写入会话 `intent`，状态进入 `ACTIVE`。

## 5.2 意图确定后的技能收敛（IDLE -> ACTIVE 过渡）
当意图已确定（不需要意图澄清）后，立即执行技能收敛流程：
1. 技能硬过滤：
   - 仅保留 `intentTag == top1Intent` 的技能。
   - 不符合当前意图的技能全部剔除。
2. 候选缩圈：
   - 对过滤后的技能按向量相似度初排，取 TopN（建议 5）。
3. LLM 精排（同意图内）：
   - 输入：最近 3 轮对话、当前意图、候选技能（名称+描述+关键参数摘要）。
   - 输出：
     - `bestSkill`（最终技能）
     - `scores`（每个候选技能 0-100 分）
     - `reason`（简短理由）
4. 执行动作：
   - 直接选择 `bestSkill` 进入 ACTIVE 链路（补槽/执行）。
   - 当前版本不引入“技能澄清”交互，避免打断用户主流程。
5. 兜底规则：
   - 若 LLM 输出无效（无 `bestSkill` 或 JSON 非法）：
     - 回退到规则选取：使用候选集向量分最高技能作为 `bestSkill`。

## 5.3 工具选择与多工具编排（IDLE 末尾）
当 `bestSkill` 已确定后，在 `IDLE` 内继续完成工具选择流程：
1. 工具候选集限定：
   - 仅加载 `bestSkill` 绑定的工具集合。
2. 工具召回：
   - 使用当前 query 向量（短期记忆 3 轮构建）与工具向量做相似度检索，取 TopK（最多 5 个）。
   - 每个候选工具携带：
     - `simScore`（相似度）
     - `heat_weight`（近 7 日热度权重）
     - `toolName` / `toolDescription`
3. LLM 决策：
   - 输入：用户 query、候选工具及其 `simScore/heat_weight/描述`。
   - 输出：`selectedTools`（可为 1~N）、`executionOrder`、`reason`。
4. 选择原则：
   - 优先单工具完成任务（默认策略）。
   - 在确有必要时允许多工具，不排斥多工具方案。
   - 不要求 LLM 输出工具依赖关系，避免依赖幻觉导致错误编排。
5. 上限控制：
   - 建议每轮工具数软上限为 3。
   - 若 LLM 选择超过上限，需给出必要性说明；执行引擎可截断为高置信前 3 个。

## 5.4 槽位范围收敛（IDLE 末尾）
在意图、技能、工具列表确定后，必须在 `IDLE` 阶段完成槽位收敛：
1. 汇总工具参数：
   - 从 `selectedTools` 提取 `inputSlots`。
   - 计算 `requiredSlots`（必填）与 `optionalSlots`（可选）。
2. 生成槽位范围：
   - `slotScope = requiredSlots ∪ optionalSlots`
3. 计算缺失槽位：
   - `missingSlots = requiredSlots - 已填槽位（实体记忆 + 当前轮抽取）`
4. 产出执行计划：
   - 将 `intent`、`bestSkill`、`selectedTools`、`slotScope`、`missingSlots` 写入会话上下文。

## 5.5 IDLE -> ACTIVE 切换条件
只有当以下执行计划要素齐全时，才允许从 `IDLE` 进入 `ACTIVE`：
- `intent` 已确定
- `bestSkill` 已确定
- `selectedTools` 已确定
- `slotScope` 已生成
- `missingSlots` 已计算

进入 `ACTIVE` 后职责：
- 仅负责“补齐 missingSlots + 执行工具”，不再做意图/技能/工具重选。

## 5.6 ACTIVE 执行规约（无用户额外交互切换）
状态切换约束：
- 当 `IDLE` 执行计划齐备后，`IDLE -> ACTIVE` 自动切换，不需要用户额外交互确认。

执行集合：
- `pendingTools`：待执行工具集合（初始为 `selectedTools`）
- `executedTools`：已执行工具集合（初始为空）

每轮 ACTIVE 处理流程：
1. 从 `pendingTools` 中逐个检查是否“可执行”：
   - 工具所需 `required slotKey` 是否都已填充。
2. 每轮先做槽位抽取（固定窗口）：
   - 使用“最近 3 轮上下文”进行抽槽：`历史 2 轮 + 当前 1 轮`。
   - 每轮规则一致，不区分“首次 ACTIVE”与“后续 ACTIVE”。
3. 可执行工具：
   - 执行工具调用。
   - 调用成功后，将该工具从 `pendingTools` 移入 `executedTools`。
   - 若工具输出补充了新槽位，更新实体记忆与当前槽位值集合。
4. 不可执行工具：
   - 记录其缺失 `slotKey`。

无可执行工具时：
- 若本轮 `pendingTools` 均不可执行：
  - 汇总缺失槽位并返回给前端。
  - 进入“引导用户填槽”交互。
  - 状态保持 `ACTIVE`，等待用户补充后下一轮继续判定。

ACTIVE 结束条件：
- 当 `pendingTools` 为空（全部迁移到 `executedTools`）时，视为本轮计划执行完成，可回到 `IDLE` 等待下一任务。
- ACTIVE 轮次建议上限：`maxActiveTurns=6`（可配置）。
- 超过上限时进入兜底：返回当前缺失槽位摘要并引导用户重述/重开任务。

ACTIVE 收尾（回到 IDLE 前）：
1. 生成最终回答：
   - 输入仅使用：`intent + skillName + executedTools + toolOutputSummaries`
   - 不依赖“最近 3 轮对话”，避免填槽对话污染最终答复。
2. 状态切换：
   - 完成最终回答后执行 `ACTIVE -> IDLE`。
3. 清理上下文（防污染）：
   - 清空执行计划：`selectedTools/pendingTools/executedTools`。
   - 清空槽位相关：`slotScope/missingSlots` 及本轮临时槽位值。
   - 清空短期对话窗口：`assistant:chat:user:{taskId}`。
   - 清空当前 `intent`，下一轮由 IDLE 重新判定。

## 5.7 工具输出处理与抽槽（ACTIVE 内）
工具执行成功后，按两步处理输出：
1. 编码标准化：
   - 依赖 AOP 完成编码修复后，在“入缓存”阶段统一重写为 UTF-8（只做一次）。
2. 输出语义提炼：
   - 将工具原始输出送给 LLM 生成简短语义摘要，作为后续推理上下文。

当存在 `missingSlots` 时，触发“基于工具输出的抽槽判定”：
- 输入：`工具输出摘要 + missingSlots + intent + skillName`
- 输出：是否抽槽、抽取到的槽位值集合

抽槽约束：
- 仅允许抽取 `missingSlots` 内槽位。
- 不在 `missingSlots` 中的槽位禁止写入实体记忆。

推荐默认参数：
- `TopK = 10`
- `confidenceThreshold = 0.35`（可配置）
- `deltaThreshold = 0.03`（可配置）

意图澄清分支要求：
- 返回“候选意图 + 澄清问题”（例如二选一或开放补充）。
- 澄清轮次上限可配置（建议 2 轮），超过后回退到更保守策略（继续提问或人工兜底）。

澄清分支细则：
- `AMBIGUOUS_TOP2`：二选一澄清，只围绕 top1/top2 意图提问。
- `LOW_CONFIDENCE`：开放澄清，不做二选一，返回候选 Top3 作为参考建议。

澄清问题生成规则（固定模板，不调用 LLM）：
- 使用模板字符串（建议配置项）：`我理解你可能是“%s”或“%s”，你现在更想做哪一个？`
- 由执行引擎使用 `String.format(template, intentAName, intentBName)` 渲染最终问题。
- `intentAName/intentBName` 必须是意图展示名（label），不直接暴露内部 intent code。

推荐返回给前端的字段：
- `state=IDLE`
- `needClarification=true`
- `clarificationType`（`AMBIGUOUS_TOP2` 或 `LOW_CONFIDENCE`）
- `taskId`
- `intentA` / `intentB`（建议同时返回 code 与 label）
- `question`（模板渲染后的最终文案）
- `candidatesTop3`（仅 `LOW_CONFIDENCE` 必填，按分数从高到低）
- `confidence` / `delta`（用于调试与可观测）

## 6. Redis 数据规范

TTL：`agent.assistant.state.ttl-minutes`（默认 30 分钟）

键设计：
- 会话状态：`assistant:state:task:{taskId}`（String JSON）
- 用户索引：`assistant:state:user:{userId}`（String，值为 taskId）
- 执行计划：`assistant:plan:{taskId}`（String JSON，存工具与槽位计划）
- 对话窗口：`assistant:chat:user:{taskId}`（List，最多 2 条用户消息）
- 实体记忆：`assistant:entity:{taskId}`（Hash，slotKey -> value）

一致性约束：
- 写状态时必须同时刷新 `taskKey` 与 `userKey` TTL。
- `userKey` 丢失但 `taskKey` 存在时，可通过 `taskId` 恢复会话。
- `assistant:plan:{taskId}` 与 `assistant:entity:{taskId}` 的 TTL 必须与会话主状态保持一致。
- TTL 统一为 `30 分钟`，采用滑动续期（每次会话读写后续期）。

## 6.1 记忆策略（当前版本）
- 本助手当前仅使用**短期记忆**，不引入中期摘要记忆和长期画像记忆。
- Prompt 上下文固定为最近 3 轮：`当前用户输入 1 轮 + 历史 2 轮`。
- 历史窗口来源于 Redis 对话列表：`assistant:chat:user:{taskId}`，最多保留 2 条历史用户消息。
- 组装规则：先取历史 2 条，再拼接当前输入，作为本轮推理输入。

## 7. LLM 推理规范

模型配置：
- `zhipu.slot-model`（默认 `glm-4-flash`）
- `zhipu.api-key`
- `zhipu.base-url`

推理分工：
1. 意图阶段：
- 实体抽取（受全局 slot whitelist 约束）
- 意图投票（输出 `needTool/answerReady/toolName`）

2. ACTIVE 阶段：
- 补槽抽取（输出 `missingSlots/answerReady/entities`）

上下文输入约束：
- 意图识别与补槽推理均使用“最近 3 轮上下文”（当前 1 轮 + 历史 2 轮）。

JSON 容错：
- 先按完整 JSON 解析；失败则截取最外层 `{...}` 再解析；仍失败返回空对象兜底。

失败兜底：
- 意图投票失败：默认 `needTool=false, answerReady=true`（可直接回答）
- 补槽失败：默认 `needTool=true, answerReady=false, missingSlots=expectedMissingSlots|[slot_fill_failed]`

## 8. 白名单与语义依赖

白名单来源优先级：
1. `agent.assistant.intent-slot-whitelist.path`
2. `agent.assistant.slot-summary.path`
3. `dataset/mcp_final_summary.json` 若干候选相对路径

读取策略：
- 启动后懒加载并缓存到内存 `globalSlotWhitelistCache`。

约束：
- 实体抽取结果若 slot 不在 whitelist 中，必须过滤掉。

## 9. 现状风险与改造点

### 9.1 现状风险
- `TOOL_EXECUTION` 尚未接真实工具执行器，语义上与 `SLOT_CLARIFICATION` 重叠。
- Controller 与 Orchestration 各自做一轮状态判定，存在策略漂移风险。
- 注释存在编码异常（中文乱码），需统一 UTF-8 无 BOM。

### 9.2 建议改造（施工顺序）
1. 统一状态判定入口：保留一处决策函数，另一处只做参数校验。
2. 为 `TOOL_EXECUTION` 接入真实执行器接口（可先 mock）。
3. 引入统一“回复生成”阶段（answer render），避免直接把 `answerReady` 当终局。
4. 增加主链路观测：`taskId/state/stage/latency/errorCode` 结构化日志。
5. 增加状态机与 stage 路由单测。

## 10. 验收标准（DoD）
- 功能：
  - 首轮必须可创建状态。
  - 两态迁移无非法分支。
  - `missingSlots` 可驱动 ACTIVE 保持。
- 稳定性：
  - Redis 异常不致进程崩溃，日志可定位。
  - LLM 返回脏 JSON 时链路可继续。
- 可观测：
  - 能基于 `taskId` 查询状态。
  - 日志能看出“当前态 -> stage -> 下一态”。

## 10.1 XXL-Job（日统计任务）
目标：
- 使用 XXL-Job 增加“工具调用日统计”任务，每天 `00:00` 执行一次。

任务定义：
- 任务名：`tool_call_daily_stat_job`（建议）
- 触发：每日 0 点（Cron 由 XXL-Job 配置）
- 统计口径：统计“今日（自然日）所有工具的调用次数”

写入目标：
- 表：`tool_call_daily_stats`
- 主键：`(stat_date, tool_name)`
- 字段：
  - `stat_date`：统计日期
  - `tool_name`：工具名
  - `call_count`：当日调用次数

幂等要求：
- 同一天重复跑任务时，按 `stat_date + tool_name` 做 UPSERT 覆盖，避免重复累加。

与热度联动：
- 该日表作为 `recent_7d_count` / `heat_weight` 的上游数据来源。
- 后续可增加第二个任务（每日或每小时）从日表聚合回填 `mcp_tool_vector`。

## 10.2 热度统计实现口径（简化版）
Redis 计数模型（避免大 Key）：
- 不使用 Hash 聚合。
- 使用 String 计数键：
  - `tool:call:daily:{yyyyMMdd}:{toolName}`
  - 值通过 `INCR` 增长
  - TTL 固定 `2 天`（仅做短期缓冲）

在线计数：
- 工具调用成功后，执行 Lua 脚本（静态文件）完成：
  - `INCR key`
  - `EXPIRE key 172800`
- Lua 脚本必须文件化（例如 `resources/lua/incr_tool_daily.lua`），禁止运行时动态拼接脚本文本。

0 点统计（XXL-Job）：
- 每天 `00:00` 统计“昨天”数据。
- 使用 `SCAN match tool:call:daily:{yyyyMMdd}:*` 扫描昨天键集合。
- 读取计数后按 `(stat_date, tool_name)` UPSERT 到 `tool_call_daily_stats`。

7 日热度计算：
- 近 7 日总调用量统一以 PG 日表为准（不依赖 Redis 历史）：
  - `recent_7d_count = SUM(call_count) over last 7 days`
- 再计算 `heat_weight` 并回填 `mcp_tool_vector`。
- 热度公式（当前固定版）：
  - `heat_weight = sigmoid(log(recent_7d_count + 1))`

PG 分区建议：
- `tool_call_daily_stats` 采用按周分区（周粒度）管理历史数据。
- 近 7 日聚合时应尽量只扫描 1~2 个周分区。

## 11. 施工清单（可直接开任务）
- Task A：修复 assistant 模块中文注释编码问题（UTF-8 无 BOM）。
- Task B：抽离统一 StateDecisionService，收敛 Controller/Orchestration 双判定。
- Task C：实现 ToolExecutorService，并挂到 `TOOL_EXECUTION` stage。
- Task D：新增 AnswerRenderService，形成“执行结果 -> 回复文本”闭环。
- Task E：补充单测：状态迁移、stage 路由、LLM 解析容错。

## 12. 异步化体验改造（RocketMQ + WebSocket）
目标：
- 将当前同步阻塞链路改为“快速受理 + 异步执行 + 实时推送”，提升前端交互体验。

主链路改造：
1. HTTP 受理层：
   - `POST /assistant/execute` 仅做参数校验、生成/接收 `taskId`、投递 RocketMQ 消息。
   - 投递成功后立即返回：`{taskId, state: \"QUEUED\"}`。
2. RocketMQ 执行层：
   - 消费者按本 spec 的 IDLE/ACTIVE 规则执行完整任务。
   - 过程中持续更新 Redis 会话状态与执行计划。
3. WebSocket 推送层：
   - 前端按 `taskId` 订阅。
   - 后端在关键节点推送事件，驱动前端状态更新。

WebSocket 订阅建议：
- 订阅入口：`/assistant/ws?taskId={taskId}`
- 推送粒度：task 级（按 taskId 路由）

事件类型建议：
- `TASK_STATE_CHANGED`：状态变化（QUEUED/RUNNING/IDLE/ACTIVE）
- `TASK_NEED_CLARIFICATION`：需要意图澄清
- `TASK_NEED_SLOTS`：需要补槽
- `TASK_TOOL_PROGRESS`：工具执行进度
- `TASK_FINISHED`：任务完成（含最终回答）
- `TASK_FAILED`：任务失败（含错误摘要）

可靠性约束：
- 生产端：RocketMQ 同步发送（broker ack）+ 发布失败重试。
- 消费端：按 `taskId` 做幂等，避免重复消费导致重复执行。
- WebSocket：仅作为通知通道，Redis 状态为真值来源。
- 断线恢复：前端可通过 `GET /api/assistant/state/task?taskId=...` 补拉最新状态。

错误分流与重试策略：
- 可恢复错误：进入 RocketMQ 延迟队列重试（不快速失败）
  - 线程池限流/拒绝（RejectedExecution 等）
  - 熔断触发
  - HTTP 调用超时
  - HTTP 状态码非 200
- 重试次数与节奏：
  - 最多重试 `3` 次（不含首次）
  - 延迟梯度：`1s -> 2s -> 5s`
  - 额外总等待约 `8s`
- 不可恢复错误：快速失败并返回友好提示（不进延迟队列）
  - 参数非法
  - 工具或技能不存在
  - 槽位校验失败/白名单强约束失败
- 延迟重试达到上限后，转最终失败并推送 `TASK_FAILED`。

---

版本：`v1.0`  
状态：`基于当前代码实现的基线 spec`  
模块：`agent-business`
