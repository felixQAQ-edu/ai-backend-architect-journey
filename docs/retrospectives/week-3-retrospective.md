# M1 Week 3 Retrospective

> **时间**:2026-05-11(Week 3 Day 1)→ 2026-05-21(Week 3 Day 5)
> **主题**:中间件三层架构落地 + ChatMemoryStore 持久化全链路
> **状态**:Week 3 主线 100% 完成,所有任务交付

---

## 1. 目标 vs 实际

### Week 1 草拟时的 Week 3 任务清单(ROADMAP)

- Token 计费 `@Aspect` 中间件
- 请求/响应日志落库
- ChatMemoryStore 持久化

### 实际产出对照

| 计划 | 实际 | 偏差分析 |
|------|------|---------|
| Token 计费 `@Aspect` | `ChatModelListener`(笔记 3/4 + ADR-006) | **框架级调整**:Day 3 接入真实流式调用后发现 `@Aspect` 在 SSE 流式拿不到 TokenUsage(笔记 3),改用 LangChain4j 原生 SPI |
| 请求/响应日志落库 | Filter + Aspect + Listener 三层中间件,职责分明 | **范围扩展**:形成完整横切关注点架构,而不只是日志落库 |
| ChatMemoryStore 持久化 | conversation_message 表 + delete-then-insert 全量替换 + 真实多轮 curl 验证(LLM 引用前一轮"Felix") | 按计划完成 + 顺手做 sessionId → conversationId 全局重命名(V3 migration) |

**Week 3 最重要的设计转折**:Day 3 启动 `@Aspect` 计费时才发现"`@Aspect` 在流式拿不到 TokenUsage"这一事实,促成 ADR-006 决定"`@Aspect` 与 `ChatModelListener` 双层职责分明"。这一发现来自笔记 6 / 笔记 3——"真实流式跨线程"与"AOP 切面退出时机"的物理冲突。

---

## 2. 工程交付清单

### 2.1 代码增量(按业务域分包)

**`billing/`**(Day 2-4 主线)
- `BillingLog` Entity + `BillingLogRepository`
- `BillingListener implements ChatModelListener`
- `BillingStatus` enum(SUCCESS / FAILED / RATE_LIMITED / ERROR_RESPONSE)
- `BillingStatusClassifier`(异常 → status 映射,8 个单测)
- 5 个测试覆盖

**`memory/`**(Day 5 主线)
- `ConversationMessage` Entity + Repository
- `MessageRole` enum(沿用 LangChain4j ChatMessageType)
- `ChatMessageConverter`(双向转换器,基于 LangChain4j `ChatMessageSerializer/Deserializer`)
- `PersistentChatMemoryStore implements ChatMemoryStore`
- 3 个测试覆盖

**`web/`**(Day 4 主线)
- `RequestIdFilter`(MDC + `X-Request-Id` 响应头)
- `RequestLoggingAspect`(`@Around` timing + `@AfterThrowing` 异常兜底)

**`config/` 调整**(Day 5)
- `ChatMemoryConfig` 从 in-memory 切到 `PersistentChatMemoryStore`
- `LangChain4jHttpClientConfig` 保留观察(待 Week 4+ 反证实验)

### 2.2 数据库 schema 增量

| Flyway 版本 | 内容 | 时机 |
|------------|------|------|
| V1 | `billing_log` 表(19 列) | Day 2 |
| V2 | `conversation_message` 表(9 列) | Day 5 |
| V3 | `billing_log.session_id` → `conversation_id` 重命名 | Day 5 |

### 2.3 文档增量

| 文档 | 时机 | 主题 |
|------|------|------|
| ADR-005 关闭 OSIV | Day 2 | SSE 长连接 + 50 并发场景下连接池保护 |
| ADR-006 `@Aspect` 与 `ChatModelListener` 职责划分 | Day 3 | 横切关注点双层架构 |
| ADR-007 ChatMemoryStore 消息持久化策略 | Day 5 | 平字段 + 非平 JSON + delete-then-insert |
| 笔记 6 跨线程回调的认知冲突 | Day 3 | 心智模型 vs 真实行为的差异 |
| 笔记 7 Day 3-4 卡点初步定位 | Day 4 | 已被笔记 8 反证(保留为"诊断失误样本") |
| **笔记 8 诊断纪律失误复盘** ⭐ | Day 4 末 | Week 3 元教训核心 |
| 笔记 9 Provider 切换零代码实证 | Day 4 末 | ADR-004 关键卖点的意外验证 |
| 笔记 10 重命名"清单漏项"陷阱 | Day 5 末 | 笔记 8 主题在 Day 5 的微缩复现 |
| CONTEXT.md v0.1 → v0.2 | Day 5 | 加 sessionId → conversationId 解决条目 |

---

## 3. 决策密度图

Week 3 是 **M1 阶段决策最密集的一周** —— 7 天内交付 3 个 ADR + 5 个 LEARNING-NOTES。

| 日期 | 产出 |
|------|------|
| Day 2 | ADR-005 关闭 OSIV |
| Day 3 | 笔记 6 跨线程回调认知;ADR-006 横切关注点双层架构 |
| Day 4 | 笔记 7(后被反证);笔记 8 诊断纪律 ⭐;笔记 9 Provider 切换实证 |
| Day 5 | ADR-007 ChatMemoryStore 持久化;笔记 10 重命名清单漏项 |

**为什么 Week 3 决策密度最高**:
- Day 1-2 还在用 in-memory 桩代码,Day 3 接入真实 LLM 调用后,"流式 / 跨线程 / 异常分类 / 持久化"等真实世界的复杂性开始密集暴露
- Week 1-2 的 ADR-001/002/003/004 是**事前选型**;Week 3 的 ADR-005/006/007 都是**代码落地过程中的"被迫面对"决策**——这种决策来自真实约束,质量比事前选型更高

---

## 4. 系统性元教训:**诊断纪律是 Week 3 的隐藏主题**

Week 3 暴露了两次**同模式失误**,跨度从 Day 3 到 Day 5:

| 失误 | 时机 | 尺度 | 模式 |
|------|------|------|------|
| 笔记 8 主题 | Day 3-4 | 大尺度("锁错根因") | 锁定"JDK 21 暗坑"假设,漏了"运行方式"这个变量 |
| 笔记 10 主题 | Day 5 | 小尺度("漏列改动清单") | 重命名时凭"记忆 + 上下文"猜清单,漏 BillingLogRepositoryTest + Replace All 没做全 |

**共性认知偏见**:用"已尝试 / 已变更列表"代替"未尝试 / 未变更空间"。

**根治方法**:用客观工具替换主观清单:
- 排查时 → "我还没换过哪个变量?"(笔记 5 排查清单 + 笔记 8 第 0 步"运行环境差异")
- 变更时 → IDE 全文搜建立完整改动清单(笔记 10 P0 前置纪律)

**Week 3 总结句**:**工程纪律比工程速度更值钱**。两次失误合计约一周时间损失,但沉淀出的两个笔记是"未来 N 次类似失误的预防针"。这两条笔记对未来工程能力提升的贡献,可能超过任何单个 ADR 决策。

---

## 5. Week 4 启动前的暖机清单

Week 4 主题:**Tool Calling 真实场景验证** —— 5 个 `@Tool` 方法的真实多轮调用 + AiServices 自动编排链路实证。

| 暖机项 | 状态 | 优先级 |
|--------|------|--------|
| `LangChain4jHttpClientConfig` 反证实验 | ⏳ 待做(保留观察) | 中 —— 不阻塞 Week 4,但 M5 压测前必须清账 |
| `BillingLogRepositoryTest` javadoc 补充 | ✅ Day 5 完成 | — |
| ROADMAP Week 4 任务清单校准 | ⏳ Week 4 启动当日 | 高 |
| Tool 方法的 happy / error 路径覆盖 | ⏳ Week 4 主线 | 高 |
| Token 单价表(BillingListener TODO) | ⏳ M2 触发点 | 低 |
| `BillingLog.provider` 硬编码 "openai" | ⏳ M2 触发点 | 低 |

---

## 6. 已知 debt(按里程碑触发点分组)

整理 Week 3 中"暂不做但已知"的事项:

### M2(引擎切换实验)
- `BillingListener.DEFAULT_PROVIDER` 硬编码 "openai" → 动态从 ChatModel 反射或工厂传入
- Token 单价表:input/output 单价当前全 0,M2 引入按 provider × model 的单价表
- ChatMessage 序列化在 OpenAI → Ollama → DeepSeek 等 Provider 间的兼容性正式验证

### M5(压测目标 50 并发)
- ChatMemoryStore delete-then-insert 写放大评估:50 并发 + 含 tool call 场景,可能需要切到 upsert by index
- BillingListener 同步落库在 SSE 长连接下的延迟影响
- HikariCP 默认 10 个连接是否需要调大
- Java 21 虚拟线程开关(已设 `spring.threads.virtual.enabled=true`,M5 压测时正式验证收益)

### M6(RAG 全链路)
- `conversation_message` 表是否需要加 embedding 字段(ADR-007 重审触发条件)
- UserMessage 多模态扩展(ChatMessageConverter 当前显式拒绝,M6+ 解锁)

### M9(高可用 + 多租户)
- ChatMemoryStore 写入异步化(Kafka 替代同步 save)
- BillingLog 异步落库 + monthly partition
- 数据出境合规(PIPL,ADR-003 已知代价 #4)
- ChatMemory 软删除字段(GDPR)

---

## 7. 对 ROADMAP 第一阶段假设的验证

ROADMAP 第一阶段(管道优先)核心假设:

> "同一套管道支撑两个领域 Agent(团队知识库 → 金融数据)"

**Week 3 对该假设的间接验证**:
- `ChatMemoryProvider` 按 memoryId 路由 = 多 Agent 共用同一套记忆基座的基础就位
- `ChatMessageSerializer` 框架 codec 兜底字段演进 = 未来加新消息类型不破坏现有数据
- Provider 切换零代码改动(笔记 9 Day 4 实证) = 换 LLM 引擎不动业务代码

**完整验证还得等 M2 实操**——Week 3 提供的是"基础设施层面没有阻挡因素"的证据,**业务复用性**还得等 M2 引擎切换 + M4-M5 切换到金融数据领域 Agent 时才能正面验证。

---

_最后更新:2026-05-21 · Week 3 收尾_
