# ADR-004 · LLM 调用框架选型（LangChain4j vs Spring AI vs 直连 SDK）

- **日期**：2026-05-06
- **状态**：已采纳
- **决策者**：Felix

## 背景

M1 Week 2 启动，要把 mock SSE 替换为真流式 LLM 调用，绕不开"用什么框架"的决定。

**M1 本期需求清单**：
- 流式输出（Token 级 SSE 推送）
- 会话记忆（多轮上下文管理）
- Tool Calling（Function Schema 定义 + Agent 调用编排）
- Token 计费中间件（按 prompt/completion token 计数落库）
- 请求/响应日志落库

**M2–M8 延伸需求**（框架选错会引发二次选型）：
- M2：换 Provider / 换模型不应改业务代码
- M3：接 Ollama / vLLM 的 OpenAI-compatible 端点
- M6–M7：RAG 全链路（解析 / 分块 / 嵌入 / 向量库 / 检索融合）
- M8：Reranker 接入、RAGAs 评估

**已锁约束**：
- Spring Boot 3.5.14（ADR-002）
- JDK 21（SETUP.md 基线）
- SSE + Spring MVC（ADR-001）
- 学习项目优先「管道跑通」而非自造轮子（第一阶段核心理念）

## 候选方案

### 方案 1：LangChain4j 1.11.x + Spring Boot Starter

**优点**：
- 生态最完整：Tool Calling、流式（`TokenStream`）、会话记忆（`ChatMemory` 多种实现）、RAG 全栈（`DocumentLoader` / `EmbeddingStoreContentRetriever` / RRF / Reranker）一条龙
- `AiServices` 注解式声明 Tool Calling 的 DX 在三者中最干净 —— 定义 Java 接口 + `@Tool` 注解，框架自动生成 Function Schema 并完成多轮 tool call 编排
- 引擎切换抽象优秀：M2 换 Provider 只改配置不动业务代码，正是 ROADMAP 第一阶段「同一套管道支撑两个领域 Agent」要验证的核心能力
- Ollama / vLLM / OpenAI-compatible 是一等公民，M3 本地模型阶段平滑过渡
- ADR-002 已验证与 Spring Boot 3.5 兼容

**缺点**：
- "Java 移植版 LangChain"的味道，配置/IoC 集成不如 Spring AI 自然
- 1.x API 仍在演进，半年内可能踩到一次小版本 breaking change
- 部分高级特性（Workflow / Agent Graph）文档稀疏，需要看源码或 Issue 区
- 没有内置 Micrometer 观测，Token 计费中间件需要自己写 AOP / 拦截器（M1 任务范围内可承担）

### 方案 2：Spring AI 1.1.x stable

**优点**：
- Spring 一等公民：`ChatClient` / `ChatModel` / `Advisor` 模式完全遵循 Spring 习惯
- 自动配置、`application.yml` 配置、Actuator/Micrometer 观测开箱即用
- Advisor 模式契合横切关注点，Token 计费 / 日志落库可直接借力
- 长期看是 Spring 官方押注的方向

**缺点**：
- 生态比 LangChain4j 年轻：RAG 组件、Tool Calling 编排、Agent 抽象都在快速演进但厚度不够，M6–M8 阶段可能需要补轮子
- Function Calling DX 不如 LangChain4j AiServices 简洁（需手写 `@Bean Function<>` 或 `MethodInvokingFunctionCallback`）
- 社区规模、Stack Overflow 答案数量小一个量级

### 方案 3：直连 SDK（OpenAI Java SDK 或 HTTP 客户端）

**优点**：
- 零框架抽象，HTTP 协议层一目了然，调试时不会"框架挡视线"
- 没有框架版本升级带来的迁移成本
- 学习收益独特：能彻底搞懂 SSE token 流、tool call 状态机、消息累加协议

**缺点**：
- M1 单月就要自己实现流式响应解析、tool call 多轮编排状态机、会话记忆 —— 每项数百行起步
- M6–M8 RAG 链路要从零搭建（解析 / 分块 / 嵌入 / 向量库客户端 / 检索融合）—— 数月级工作量
- 与「管道优先」核心阶段目标直接冲突，把精力消耗在重新发明框架而不是验证业务管道
- 简历叙事弱：「我手写了一个 mini LangChain4j」匹配岗位需求度，远不如「我用 LangChain4j 构建了完整 RAG 系统」

## 最终决策

**方案 1 — LangChain4j 1.11.x + Spring Boot Starter**

### 关键理由

1. **匹配 M1 阶段目标**：`AiServices` 把 Tool Calling 从「定义 Function Schema → 多轮 tool call 编排 → 结果回填」压缩成「声明一个接口」，节省的时间直接喂给 Token 计费中间件、日志落库这些主线任务
2. **匹配 M6–M8 阶段目标**：RAG 组件齐全度明显领先 Spring AI，到时候少踩坑，把精力留给 Semantic Chunking 调参、Reranker 选型、RAGAs 评估等真正的业务调优
3. **验证架构核心假设**：ROADMAP 第一阶段「同一套管道支撑两个领域 Agent（团队知识库 → 金融数据）」的可行性，依赖框架的 Provider/Model 切换抽象。LangChain4j 在这点上最成熟
4. **本地模型路径已铺好**：M3 接 Ollama / vLLM 时是改 1 行配置，不是改架构
5. **ADR-002 兼容性已验证**：Boot 3.5 + LangChain4j 1.11 是社区主流组合，生态稳定性最高

## 已知代价

1. **观测性需自建**：放弃 Spring AI 内置 Micrometer 集成，Token 计费、调用延迟、错误率指标需要在 M1 通过 AOP / 拦截器实现。这正好是 M1 的"中间件"学习任务，代价可接受
2. **Spring 生态深度学习有损失**：Advisor 模式、Spring AI 自动配置等 Spring-native 特性不会接触到。补救方式：M1 实现 Token 计费中间件时刻意走 AOP + Spring Interceptor 路径，把 Spring 横切关注点的肌肉记忆补回来
3. **小版本 breaking change 风险**：LangChain4j 1.x 仍在演进，半年内可能踩到一次。缓解方式：版本锁定（不用 `latest.release`），升级前看 release notes
4. **未来若整体技术栈倾向 Spring 全家桶**：Spring AI 2.0 GA 后可能需要做一次迁移评估。预计改造点：`ChatLanguageModel` → `ChatModel`、`AiServices` → `ChatClient` + Advisor、Tool 注册方式重写

## 重新审视的触发条件

- LangChain4j 1.x 出现影响项目主线的 breaking change（无替代 API 或迁移成本 > 1 周）
- M6–M8 阶段发现 LangChain4j RAG 组件不能满足实际需求（如 Hybrid Search 不支持 RRF 调权、Reranker 集成困难）
- Spring AI 2.0 GA 且生态成熟度显著反超（社区规模、组件齐全度）
- M2 引擎切换实测发现抽象渗漏严重（业务代码不得不感知 Provider 差异）

## 实际效果(事后补充)

**M1 Day 4 末实测**:

- AiServices 模式 Tool Calling 跑通:5 个 @Tool 注解方法自动生成 Function Schema,LLM 拿到完整 tool list(实测 OpenAI gpt-4o-mini 收到的请求 body 含完整 schema,见日志样本)
- **Provider 切换"零代码改动"已被意外验证**:application.yml 占位符 `${LLM_BASE_URL}` / `${LLM_API_KEY}` / `${LLM_MODEL_NAME}` 配合环境变量,在 OpenAI 和 DeepSeek 间切换业务代码无任何修改,BillingListener 在两个 Provider 下都正确触发(详见 LEARNING-NOTES 笔记 9)
- ChatModelListener SPI 在 1.11 的 streaming + tool calling 场景下稳定,onRequest / onResponse / onError 跨线程 attributes 传递无丢失(详见 ADR-006 实际效果)
- 未踩到 1.11 版本的 breaking change(spring-starter 用的是 beta19,但 core 用 1.11.0 稳定版)

**M2 引擎切换实验时继续回填**:换到 Ollama 本地模型时业务代码改动量是否真的为零?
