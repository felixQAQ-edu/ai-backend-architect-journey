# ADR-007 · ChatMemoryStore 消息持久化策略

- **日期**:2026-05-21
- **状态**:已采纳
- **决策者**:Felix

## 背景

M1 Week 3 Day 5 启动 ChatMemoryStore 持久化层,Week 3 主线收尾任务。Day 4 末已完成 Token 计费中间件 + 横切关注点三层架构(Filter + Aspect + Listener),Week 3 剩 ChatMemory 持久化未做。

LangChain4j 1.11 提供 `ChatMemoryStore` SPI(`dev.langchain4j.store.memory.chat.ChatMemoryStore`,三方法):

```java
List<ChatMessage> getMessages(Object memoryId);
void updateMessages(Object memoryId, List<ChatMessage> messages);
void deleteMessages(Object memoryId);
```

官方文档明确语义(Step 1 摸底关键发现):

- `updateMessages()` 每次新 ChatMessage 加入 ChatMemory 时被调用,通常一次 LLM 交互发生**两次**(UserMessage 加入时一次,AiMessage 加入时再一次)
- 每次传入的是 memoryId 对应的**全量** messages 列表,不是增量 append
- evict(messageWindow 截断)时,updateMessages 收到的列表**不含**被 evict 的消息

这一"全量替换"语义决定了 store 实现策略**不是**直觉的"append 一条新消息到表里",而必须在每次调用时把表里旧记录和传入的新列表对齐。这是本 ADR 要回答的核心问题。

约束条件:

1. M1 阶段只用关系数据库(H2 dev / PostgreSQL prod),向量数据库要到 M6 RAG 才引入
2. CONTEXT.md v0.1.1 已定义 `conversation/conversationId` 为业务层概念,`memoryId` 为技术层概念,API 字段统一用 `conversationId`
3. Day 4 已确立 ADR-005(关闭 OSIV)+ ADR-006(Listener 同步落库)的整体节奏,Day 5 应保持一致
4. M1 预估并发 50,M5 压测目标 50 并发(ADR-001)
5. M1 Week 4 即将启动 Tool Calling 真实场景验证(已含 5 个 `@Tool` 方法),ChatMessage 多态结构(含 toolExecutionRequests)必须在表结构里正确表达

## 候选方案

### 方案 A:整段 JSON 存(一行一会话)

`conversation` 表只有 `conversation_id` + `messages_json`,用 LangChain4j 自带 `ChatMessageSerializer.messagesToJson` 序列化整个 List。

**优点**:
- 实现最简单,3 行代码搞定 store 三方法
- 序列化反序列化由框架负责,完全不用关心 ChatMessage 多态

**缺点**:
- 单条记录可能极大(20 条消息 × 含 tool call 的 AiMessage,几 KB-几十 KB)
- SQL 查询能力为 0:无法"找出所有提到 X 的消息"、"统计每个会话的 tool call 次数"、"按时间排序"
- 未来 RAG / BI / audit 都要解出来再做,数据库降级为"文件系统包装层"
- 与 Day 5 启动前已决定的"结构化拆字段未来友好"设计目标背离

### 方案 B:完全结构化拆三表

主表 `conversation_message` + 子表 `tool_execution_request` + 子表 `tool_execution_request_argument`(把 ToolExecutionRequest.arguments JSON 内部展开)。

**优点**:
- 关系建模教科书级别"正确",任何字段都能 SQL 查询

**缺点**:
- 学习项目阶段严重过度设计,3 张表的 JOIN 增加调试复杂度
- `ToolExecutionRequest.arguments` 本身是 LLM 输出的任意 JSON 字符串,**模式不固定**,强拆等于反复造轮子
- 每条消息落库要 1+N 次 insert,delete-then-insert 策略下放大严重
- LangChain4j 未来若新增消息类型(ImageMessage 等),表结构改动量大

### 方案 C:平字段 + 非平 JSON(本 ADR 采纳)

`conversation_message` 一张表。**ChatMessage 的"平"部分拆字段**(role / text / created_at / 等),**"不平"部分(toolExecutionRequests 这种 nested 结构)保持 JSON**。

**优点**:
- 主结构 SQL 查询友好:role 计数、消息总条数、用户 vs AI 分布、按 conversation 排序等都是 1 行 SQL
- 非平字段保持 JSON,避免反复拆解 LLM 任意输出
- 与 BillingLog 已建立的"一张表一个实体"模式一致,认知负担小
- 未来若 RAG 给消息做 embedding,主键 + text 字段直接喂 embedding pipeline
- 未来若 BI 统计 tool 调用频次,JSON 字段也能用 PostgreSQL `jsonb` 操作符查(不是死路,只是不如平字段直观)

**缺点**:
- `tool_execution_requests_json` 内部不能直接 JOIN,仅适合"读取并反序列化"而非聚合查询——本 ADR 接受这一权衡

## 最终决策

**方案 C — 平字段 + 非平 JSON**,配套四个实现细节:

### 1. 表结构

```sql
CREATE TABLE conversation_message (
    id                            BIGSERIAL    PRIMARY KEY,
    conversation_id               VARCHAR(64)  NOT NULL,
    message_index                 INTEGER      NOT NULL,
    role                          VARCHAR(32)  NOT NULL,
    text                          TEXT,
    ai_message_payload_json       TEXT,
    tool_call_id                  VARCHAR(64),
    tool_name                     VARCHAR(128),
    created_at                    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_conv_msg_idx UNIQUE (conversation_id, message_index)
);

CREATE INDEX idx_conv_msg_conv_id ON conversation_message(conversation_id);
```

字段语义:

| 字段 | 含义 | 可空 |
|------|------|------|
| `conversation_id` | LangChain4j memoryId 的 String 形式,对外即 CONTEXT.md 定义的 conversationId | NOT NULL |
| `message_index` | 在该 conversation 内的 0-based 顺序号,evict 后会重写 | NOT NULL |
| `role` | `USER` / `AI` / `SYSTEM` / `TOOL_EXECUTION_RESULT` 四值,沿用 LangChain4j `ChatMessageType` 枚举命名 | NOT NULL |
| `text` | 消息正文 | nullable(纯 tool-call 的 AiMessage 可能没 text) |
| `ai_message_payload_json` | 仅 AI role 行填:整个 AiMessage 由 LangChain4j `ChatMessageSerializer.messageToJson(am)` 生成,框架自动兜底 text/thinking/toolExecutionRequests/attributes 字段集演进 | nullable |
| `tool_call_id` | ToolExecutionResultMessage 关联的 tool call id | nullable |
| `tool_name` | ToolExecutionResultMessage 携带的 tool 名 | nullable |
| `created_at` | 入库时间戳,**不是**消息在 LLM 侧的时间 | NOT NULL |

**`(conversation_id, message_index)` UNIQUE 约束的设计意图**:Day 5 走 delete-then-insert 时这个约束不直接发挥作用,但**保留了未来切换到 upsert by index 策略的可能性**,演进时不用改表。

### 2. 实现策略:delete-then-insert

`updateMessages(memoryId, messages)` 伪代码:

```java
@Transactional
public void updateMessages(Object memoryId, List<ChatMessage> messages) {
    String convId = memoryId.toString();
    repository.deleteByConversationId(convId);
    for (int i = 0; i < messages.size(); i++) {
        repository.save(toEntity(convId, i, messages.get(i)));
    }
}
```

**关键理由**:与 LangChain4j 官方"全量替换"语义零歧义对齐,无 evict 边界 bug 风险。M1 阶段无并发对同一 conversation 写入的场景,写放大代价可接受。M5 / M9 压测发现瓶颈再演进到 upsert by index。

### 3. 写入时机:同步落库

updateMessages / getMessages 都在 `chat()` 调用栈内同步执行,沿用 ADR-005(关闭 OSIV)+ ADR-006(Listener 同步落库)的整体节奏。

**已知代价标注**:同步写意味着 `chat()` 的端到端延迟包含 2 次 DB 写(UserMessage 触发一次、AiMessage 触发一次)。Day 4 实测 BillingLog 同步 save < 30ms,Day 5 走同样路径预期同量级,SSE 长连接总耗时几秒级别,DB 写不会成为感知瓶颈。

### 4. MessageWindow 配置:maxMessages = 20

LangChain4j 示例默认 10,Quarkus 默认也是 10。M1 接入 5 个 `@Tool` 后,Tool Calling 一来一回就占 2 条消息(AiMessage with toolExecutionRequests + ToolExecutionResultMessage)。20 提供约 5–7 轮含 tool 的对话余量,平衡上下文长度与 LLM 计费成本。

### 关键理由汇总

1. **匹配 Day 5 启动前已锁的设计目标**——结构化拆字段(未来友好)+ 同步写(节奏一致)+ delete-then-insert(语义清晰)
2. **复用 Day 4 已建立的模式**——Flyway migration + JPA Entity + Spring Data Repository 三件套,Day 5 重复一次固化肌肉记忆
3. **正确应对"全量替换"语义陷阱**——这是 LangChain4j ChatMemoryStore SPI 最容易踩坑的点,delete-then-insert 是最简单的正确解,实现层面无 evict 边界 bug 风险
4. **保留所有演进路径**——表结构的 UNIQUE 约束 + 索引 + 字段宽度设计,未来切 upsert by index / 加 BI 字段 / 接 embedding pipeline 都不需要重建表

## 已知代价

1. **写放大**:20 条消息的会话,每次新增 1 条消息会触发 ~20 行 delete + ~20 行 insert(实际新增后会到 21,然后 evict 回 20,所以是 20 delete + 20 insert)。M1 单用户场景 PostgreSQL 完全无压力,M5 压测 50 并发时如果总写量超过 1000 行/秒需要重新评估并切到 upsert by index。

2. **`ai_message_payload_json` 内部不可直接 SQL 查询**:想统计"哪个 tool 被调用最多"或"AiMessage 的 thinking 长度分布"需要应用层先 SELECT 出来再解 JSON,或者用 PostgreSQL `jsonb` 操作符。可接受——真要做这种分析时,**正确的做法是在 BillingListener 旁边建一张 `tool_call_log` 表**,而不是改 ChatMemory 表结构。两者关注点不同(ChatMemory 是 LLM 看的上下文,tool_call_log 是 BI 看的事件流)。

   **用 LangChain4j 自带 `ChatMessageSerializer` 而非手写 Jackson 序列化的关键好处**:AiMessage 字段集在 1.x 仍在演进(已加 `thinking` / `attributes` 等,未来还可能加更多),框架 codec 自动跟随,Day 5 写的代码无需关心未来新增字段。这一选择呼应 ADR-004 "管道优先,不造轮子"原则。代价是字段名 `ai_message_payload_json` 比"显式列出存了什么"略抽象,但 javadoc 中已注明该字段仅对 AI role 行有意义。

3. **`role` 字段用 enum + `@Enumerated(EnumType.STRING)`**:Entity 层定义 `MessageRole` 枚举(`USER` / `AI` / `SYSTEM` / `TOOL_EXECUTION_RESULT`),DB 列声明 VARCHAR(32),与 Day 4 的 `BillingStatus` 风格统一,学习项目优先模式一致性。代价:LangChain4j 在 `ChatMessageType` 中新增类型时(如 ImageMessage 升为顶层消息),需要先加 enum 常量再部署。但这其实是好事——显式的"变更检查点"信号,而非默默吞下未知类型。VARCHAR(32) 给未来 enum 值留余量(当前最长 `TOOL_EXECUTION_RESULT` 21 字符)。

4. **`tool_call_id` / `tool_name` 字段在非 `TOOL_EXECUTION_RESULT` role 时永远为空**:表"宽"了一些(2 个永远为 null 的列),但 4 种 role 共用一张表的简洁性 > 拆 sub-table 的洁癖。M1 阶段优先选简洁。

5. **`@MemoryId` 默认值陷阱**(Step 1 摸底发现):LangChain4j 文档明确,如果 AiServices 方法没有 `@MemoryId` 参数,memoryId 默认是字符串 "default"。**这意味着 ChatController 必须显式接收 conversationId 并透传给 AiServices**,否则所有用户共用一份记忆(严重 bug)。本 ADR 要求 Step 5 实现时强制 conversationId 不能为空。

## 重新审视的触发条件

- M5 压测发现 delete-then-insert 在 50 并发下成为瓶颈(预期不会,但需验证)
- M6 RAG 阶段需要给历史消息建 embedding 索引,届时表结构可能需要加 `embedding_vector` 字段或拆出 embedding 子表
- LangChain4j 2.x 修改 ChatMessage 多态结构,新增消息类型需要扩展表字段
- 引入消息流水审计 / GDPR 删除需求,需要软删除字段(`deleted_at`)
- 单个 conversation 消息数超过 100,maxMessages=20 截断后,被 evict 的消息是否需要保留为"长期历史"(目前不保留,evict 即真删)

## 实施进度

- ✅ **Step 2**:Flyway migration `V2__init_conversation_message.sql` + `ConversationMessage` JPA Entity + `MessageRole` enum + Schema 测试(沿用 BillingLog 模式,Day 5 完成)
- ✅ **Step 3**:`ConversationMessageRepository`(JpaRepository + 显式 `@Modifying @Query` 删除)+ `ChatMessageConverter`(Spring `@Component`,基于 `ChatMessageSerializer/Deserializer` 双向转换,7 个单测覆盖 4 种 role roundtrip + 多模态拒绝 + payload 缺失异常)
- ✅ **Step 4**:`PersistentChatMemoryStore implements ChatMemoryStore`,delete-then-insert 同步落库,`Repository.deleteByConversationId` 加 `@Modifying(flushAutomatically=true, clearAutomatically=true)` 防御测试嵌套事务下 pending INSERT 未 flush 的陷阱,6 个集成测试覆盖 getMessages 空/非空 / updateMessages 全量替换 / tool call 顺序保持 / 不同 conversation 隔离
- ✅ **Step 5**:`ChatMemoryProvider` Bean(`ChatMemoryConfig` 接入 `PersistentChatMemoryStore`,maxMessages=20)+ ChatController 接收 conversationId + @NotBlank @Valid 强校验空字段返回 400 + AiServices `@MemoryId` 路由
- ✅ **Step 6**:真实 curl 多轮对话验证通过 — conversationId `test-multi-turn-001` 三轮(SystemMessage + UserMessage "我叫 Felix" + AiMessage 确认 + UserMessage "我叫什么名字" + AiMessage "你叫 Felix")5 行落库,message_index 0-4 顺序正确,role 四种类型全部正确;空 conversationId 返回 HTTP 400(@NotBlank 强校验生效)

## 实际效果(事后补充)

**M1 Day 5 末实测**(2026-05-21):

1. **多轮记忆真实生效**:同一 conversationId 跨 HTTP 请求,LLM 在第二轮提问"我叫什么名字?"时正确回答"Felix",证明 PersistentChatMemoryStore 的 getMessages 在每次 LangChain4j chat() 调用时被正确拉起,历史消息进入 prompt context。

2. **结构化拆字段设计被实测验证为对的选择**:DB 表里 5 行数据,用 H2 console 一条 `SELECT message_index, role, text FROM conversation_message WHERE conversation_id = ?` 即可人眼可读地审查全部对话历史——这正是方案 C(平字段+非平 JSON)对方案 A(整段 JSON)的关键优势。如果当初选 A,审查同样内容需要应用层反序列化 JSON,无法在 DB 工具里直接查。

3. **@MemoryId 默认 "default" 陷阱被强校验拦截**:空 conversationId 触发 @NotBlank,Spring 自动返回 HTTP 400 而不是悄悄进入 LangChain4j 的 default memoryId 共享池。这是 ADR-007 已知代价 #5 的有效防御。

4. **delete-then-insert 写放大无感知**:Day 5 末实测单用户连续 3 轮对话场景下,5 行的 conversation_message 表读写延迟肉眼不可感(LLM 流式生成本身就是几秒级,DB 写放大被掩盖)。M5 压测 50 并发场景再正式回填。

5. **CONTEXT.md 术语对齐的副作用**:Day 5 顺手做了 sessionId → conversationId 全局重命名(影响 9 个文件 + V3 Flyway migration),整个过程暴露了一次"列改动清单时漏文件"的诊断纪律失误(BillingLogRepositoryTest 未列入),修复后 IDE 全文搜确认遗漏完整性,详见 LEARNING-NOTES。

**M5 压测时回填**:50 并发下 ChatMemoryStore 写放大的实际影响。

**M2 引擎切换时回填**:换 Provider(OpenAI → Ollama)时 ChatMessage 序列化/反序列化是否完全跨 Provider 无感。
