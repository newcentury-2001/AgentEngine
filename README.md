# AgentEngine

面向多工具调用(MCP)的任务型 Agent 工程。核心目标不是“闲聊”，而是“可执行”:
- 识别用户意图
- 选择合适技能和工具
- 自动填槽与追问补槽
- 调用工具并汇总结果

---
目前项目还没有完全完善，只是一个初始版本

## 1. 项目定位

本项目采用“离线构建 + 在线执行”双链路:
- 离线链路(`agent-ops`): 工具导出、语义清洗、向量化、入库
- 在线链路(`agent-business`): 状态机编排、检索路由、工具执行、结果回传

这样可以把“重计算、慢更新”的部分放到离线，把“低延迟、可恢复”的部分放到在线。

---

## 2. 模块结构

- `agent-business`
  - 对话主链路
  - 状态机(IDLE/ACTIVE)
  - WebSocket 实时推送
  - 工具执行与重试
- `agent-ops`
  - MCP 工具描述导出
  - 语义清洗(槽位、描述、标签)
  - Embedding 任务入队与消费
  - 运维脚本与初始化能力
- `agent-common`
  - HTTP 客户端封装
  - 线程池装饰器(限流/熔断)
  - 公共模型与协议封装

---

## 3. 在线主链路思路

### 3.1 IDLE 阶段
1. 读取短期上下文(最近对话窗口)。
2. 向量检索技能(topK)并做意图聚合。
3. 在目标意图下做技能精排与工具收敛。
4. 计算本轮执行计划(plan): pendingTools、missingSlots、slotScope。
5. 预抽槽(可抽则填，不可抽不强行填)。
6. 满足条件后切到 ACTIVE。

### 3.2 ACTIVE 阶段
1. 先做“意图漂移检测”(是否需要切回 IDLE 重规划)。
2. 不漂移则继续补槽与可执行工具筛选。
3. 执行工具，分析输出，更新实体和计划。
4. pending 为空则生成最终回答并结束。
5. 结束时清理 Redis 上下文，防止跨任务污染。

---

## 4. 检索与数据建模思路

### 4.1 语义解耦
- `slotKey`: 业务语义槽位(统一语义层)
- `field`: 工具真实参数名(实现层)

调用时通过 `slotKey -> field` 映射组装参数，避免跨工具字段名差异导致的断裂。

### 4.2 向量检索
- 技能和工具向量入 PG(支持 pgvector)。
- 在线侧优先数据库检索，不在应用内全量加载后手算相似度。

---

## 5. 可靠性策略

### 5.1 工具执行
- 独立第三方 HTTP 线程池
- 零队列(`SynchronousQueue`)防止无界堆积
- 限流 + 熔断(装饰器)

### 5.2 失败分流
- 可恢复错误(限流、熔断、超时、429、5xx、连接抖动): RocketMQ 延迟重试
- 不可恢复错误: 快速失败并返回友好提示

### 5.3 重试链路
- 生产端负责延迟入队
- 消费端负责重试执行并推进状态
- 避免“只入队不消费”的假恢复

---

## 6. 抽槽策略演进(关键经验)

### 6.1 rawOutput 优先
抽槽主输入使用 `rawOutput`，不再依赖 `summary`。

### 6.2 元字段去污染
统一剔除 `taskNo/requestId/traceId/taskId/jobId`，避免误写业务槽位。

### 6.3 规则优先 + LLM 兜底
- 先结构化规则抽取
- 再让 LLM 兜底缺失槽位
- 降低幻觉与串位风险

### 6.4 缺参与摘要分离
- 缺参判断优先代码规则
- 缺参时不生成 summary
- summary 专注“结果摘要”而不是“错误解释”

---

## 7. MCP 鉴权模式

支持按技能配置鉴权模式:
- `header`: 仅请求头 Authorization
- `query`: 仅 URL 查询参数 Authorization
- `both`: 同时带 header + query

示例:

```yaml
agent:
  assistant:
    mcp-auth:
      default-mode: header
      query-mode-skill-names: dream-interpretation
      query-param-name: Authorization
```

---

## 8. 关键配置(agent-business)

- `agent.assistant.tool-http.*`: 工具调用线程池/限流/熔断
- `agent.assistant.tool-http.retry.*`: 延迟重试配置
- `agent.assistant.mcp-auth.*`: MCP 鉴权模式
- `rocketmq.name-server`: RocketMQ NameServer 地址
- `spring.datasource.*`: PostgreSQL
- `spring.data.redis.*`: Redis

---

## 9. 常见问题

1. RocketMQ 消费者启动失败:
- 检查 `consumeThreadNumber <= consumeThreadMax`

2. 工具返回 `status=203,msg=没有信息`:
- 常见是业务无匹配，不一定是网络失败
- 这类应按 `NO_DATA` 语义处理

3. ACTIVE 无法切换意图:
- 需要在 ACTIVE 每轮前做意图漂移判定

---

## 10. 快速启动

1. 启动依赖:
- PostgreSQL
- Redis
- RocketMQ(NameServer + Broker)

2. 启动服务:
- `agent-business`
- `agent-ops`(需要离线构建时)

3. 编译:

```bash
mvn -pl agent-business -am clean compile -DskipTests
```

---

## 11. 后续演进建议

1. 将槽位校验规则配置化(按 slotKey 类型)。
2. 增强 NO_DATA 分支(关键词改写、引导重试)。
3. 加入分布式锁，保证同 taskId 在多实例下串行推进。
4. 增加端到端可观测性(指标、trace、重试原因分布)。

---

更多实践记录见根目录 `笔记.md`。
---
