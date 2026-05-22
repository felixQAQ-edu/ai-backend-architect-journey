# AI 后端架构师 · 一年学习与建设计划

> 从应用层 → 底层推理 → RAG 架构 → 高可用 SaaS 的完整跨越

## 项目背景

这是一个为期一年(2026-04-30 → 2027-04-30)的系统性学习与工程实践计划,
最终交付一个上线运营、有付费用户的 AI SaaS 系统。

完整规划见 [docs/ROADMAP.md](docs/ROADMAP.md)。

## 当前进度

- **阶段**:第一阶段 · 管道优先
- **月份**:M1 · Week 3 已收尾
- **Day 4 完成**:Token 计费中间件五步全过(SUCCESS / ERROR_RESPONSE / FAILED 三类样本落库验证)+ RequestIdFilter + RequestLoggingAspect + 跨线程 attributes 上下文传递 + Provider 切换零代码实证(详见 LEARNING-NOTES 笔记 8、9)
- **Day 5 完成**:ChatMemoryStore 持久化全链路 — Flyway V2 conversation_message 表 + ChatMessage 4 种 role 双向转换器 + PersistentChatMemoryStore(delete-then-insert 全量替换)+ ChatMemoryProvider 接入 AiServices + 真实 curl 多轮验证(LLM 引用前一轮 "Felix")+ 顺手 sessionId → conversationId 全局重命名(V3 migration);详见 ADR-007 + LEARNING-NOTES 笔记 10
- **下一步(Week 4)**:Tool Calling 真实场景验证 — 5 个 @Tool 方法的真实多轮调用 + Function Schema 在 AiServices 自动编排链路上的实证

## 阶段目录

- `chat-pipeline/` — M1:团队知识库问答 Agent(Spring Boot 3.5 + LangChain4j + SSE 流式 + Tool Calling + 多轮记忆 + ChatMemoryStore 持久化)
- _后续阶段目录将随进度建立_

## 技术决策记录(ADR)

- [ADR-001: 流式输出技术选型 — SSE + Spring MVC](docs/adr/ADR-001-sse-vs-webflux.md)
- [ADR-002: Spring Boot 版本选型 — 3.5 vs 4.0](docs/adr/ADR-002-spring-boot-version.md)
- [ADR-003: 跨境部署节点与支付通道策略](docs/adr/ADR-003-cross-border-deploy.md)
- [ADR-004: LLM 调用框架选型 — LangChain4j vs Spring AI vs 直连 SDK](docs/adr/ADR-004-llm-framework.md)
- [ADR-005: 关闭 Open Session In View(OSIV)](docs/adr/ADR-005-disable-osiv.md)
- [ADR-006: @Aspect 与 ChatModelListener 的职责划分](docs/adr/ADR-006-listener-vs-aspect.md)
- [ADR-007: ChatMemoryStore 消息持久化策略](docs/adr/ADR-007-chat-memory-store-persistence.md)

## 技术栈

- **后端**:Java 21、Spring Boot 3.x
- **AI**:LangChain4j / Spring AI、Ollama、vLLM、BGE Embedding & Reranker
- **基础设施**:Redis、Kafka、Milvus / Qdrant、Prometheus + Grafana
- **商业化**:Stripe
