# chat-pipeline · 项目共通语言

> M1 阶段团队知识库问答 Agent 的领域术语词典。
> 本文档与 ADR 体系互补:ADR 记录"为什么这样决定",本文档记录"我们用哪个词指什么"。
> 写法参考 mattpocock/skills 的 CONTEXT.md 模式。
>
> **版本**:v0.1.1(2026-05-15,M1 Week 3 Day 3)
> **维护原则**:边写边长。新增术语的触发条件是"代码或文档里反复出现且产生过歧义",而非"我觉得这个概念将来可能要用"。

## Language

**Token**:
LLM 模型处理的最小语义单位,分为 prompt token(输入)和 completion token(输出),由模型 tokenizer 决定边界。**只用于计费和上下文长度语境**(BillingLog、ChatMemory window)。
_Avoid_: 不要用 token 指代 SSE 推送的内容片段——那是 chunk,见下条。两者不一一对应(一个 chunk 可能包含多个 token,反之亦然)。

**Chunk**(或 streaming chunk):
SSE 单次推送给前端的数据片段,对应 LangChain4j `StreamingChatResponseHandler.onPartialResponse` 的一次回调。是**传输层**单位,不是模型层单位。
_Avoid_: 不要叫它 "token"、"SSE token"、"streaming token"。

**Conversation**:
一段多轮对话的整体,业务层概念,有自己的 ID、归属用户、可被持久化和列表查询。是 `/api/conversations/{id}` 这类 API 路径里使用的词。
_Avoid_:
- 不用 `Session`:与 HTTP Session、Hibernate Session 冲突,撞名风险高
- 不用 `Chat`(单独使用时):指代不清,容易和"聊天"动作混淆

**ChatMemory**:
LangChain4j 提供的接口,**单个 Conversation 在内存或存储层的上下文表示**——存历史消息列表 + 提供 messageWindow / tokenWindow 截断策略。是 Conversation 的**技术实现细节**之一,不是同义词。
_Avoid_: 不用 ChatMemory 指代业务层 Conversation。"用户的对话历史"在产品语境下叫 conversation history,不叫 chat memory。

**RequestId**:
一次 HTTP 请求的唯一标识,UUID。由 Servlet Filter 在请求入口生成,**全链路只产生一次**,通过三种载体在不同代码层流动:
- HTTP 响应头 `X-Request-Id`(给前端/客户端)
- MDC key = `requestId`(给同步日志输出)
- `ChatModelRequestContext.attributes()` key = `requestId`(给跨线程的 ChatModelListener)

_Avoid_:
- 不用 `traceId`:虽然 LEARNING-NOTES 笔记 4 历史上用过,但 traceId 在 OpenTelemetry / Spring Cloud Sleuth 语境里有专门含义(分布式调用链),容易混。M9 引入分布式追踪后,traceId 会成为**跨服务标识**,与单请求级别的 requestId 不同一回事。
- 不用 `correlationId`:同样有特定行业含义(消息队列关联)。

## Relationships

- 一个 **Conversation** 由若干 **HTTP Request** 组成,每个 Request 携带一个 **RequestId**。
- 每个 Request 对应**一次** LLM 调用,产生**一条** BillingLog 记录(无论成功失败)。
- 每个 Request 流式响应期间产生**多个 Chunk**,Chunk 总和对应**一组 completion Token**(模型角度) / 一段 completion 文本(用户角度)。
- **横切关注点分层**(权威表见 ADR-006):
  - RequestId 注入、MDC 装填 → Filter
  - 请求 timing、Controller 异常兜底 → @Aspect
  - Token 计数、BillingLog 落库 → ChatModelListener
  - 流式 chunk 推送 → StreamingChatResponseHandler

## Flagged ambiguities

- **"Token" 早期同时指模型 token 和 SSE 推送单位** —— 已统一:模型 token 叫 token,SSE 推送单位叫 chunk。LEARNING-NOTES 笔记 4 历史用词将在下次更新时同步修正。
- **"requestId / traceId" 早期混用** —— 已统一为 `requestId`。原 LEARNING-NOTES 笔记 4 中 `MDC 装 traceId` 应改为 `MDC 装 requestId`。
- **BillingLog 失败状态分类** —— 已在 Day 2 锁定为 `SUCCESS` / `FAILED` / `RATE_LIMITED` / `ERROR_RESPONSE` 四值;`FAILED` 是兜底类(网络错误、未知异常),`RATE_LIMITED` 专指 HTTP 429,`ERROR_RESPONSE` 专指 provider 返回 4xx/5xx 业务错误。详见 ADR-006 实施进度表。
- **`orphan-xxx` 前缀**(2026-05-20 引入):当 BillingListener 在 onResponse / onError 路径拿不到 attributes 里的 requestId 时(例如某种异常路径上游 Filter 没装填),用 `orphan-` + 8 位 UUID 短码兜底落库,保证审计记录"必落"。后续 SQL 查询可用 `request_id LIKE 'orphan-%'` 识别这类记录。
- **BillingLog.provider 字段**:M1 阶段硬编码 "openai",M2 引擎切换时改成动态(从 ChatModel 实现类反射或 listener 工厂传入)。
2026-05-15:LEARNING-NOTES 笔记 3/4 已按本文档口径修订完毕
---

## 边写边长清单(暂不定义)

下列术语**已经在 ROADMAP / ADR 中出现**,但当前阶段还未产生混淆,等到实际编码遇到歧义时再回填:

- `Provider` / `Model`:M2 引擎切换时定义
- `Tool` / `Function` / `Agent`:M1 Week 4 接入 Tool Calling 时定义
- `Heartbeat` / `Reconnect`:SSE 长连接稳定性问题暴露时定义
- `Tenant` / `Workspace`:M9 多租户阶段定义
- `Embedding` / `Chunk`(RAG 语境的文档分块,与本文档现有 streaming chunk 不同):M6 RAG 阶段定义,届时需要 _Avoid_ 列表区分两个 chunk

---

_最后更新:2026-05-15 · v0.1 初版,M1 Week 3 Day 3_
