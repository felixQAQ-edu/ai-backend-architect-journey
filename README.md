# AI 后端架构师 · 一年学习与建设计划

> 从应用层 → 底层推理 → RAG 架构 → 高可用 SaaS 的完整跨越

## 项目背景

这是一个为期一年（2026-04-30 → 2027-04-30）的系统性学习与工程实践计划，
最终交付一个上线运营、有付费用户的 AI SaaS 系统。

完整规划见 [docs/ROADMAP.md](docs/ROADMAP.md)。

## 当前进度

- **阶段**：第一阶段 · 管道优先
- **月份**：M1 · Week 1
- **本周任务**：搭建 Spring Boot 3.x 脚手架，实现首个 SSE 流式端点

## 阶段目录

- `m1-knowledge-agent/` — M1：团队知识库问答 Agent（Spring Boot × LLM 全链路）
- _后续阶段目录将随进度建立_

## 技术决策记录（ADR）

- [ADR-001: 流式输出技术选型 — SSE + Spring MVC](docs/adr/ADR-001-sse-vs-webflux.md)

## 技术栈

- **后端**：Java 21、Spring Boot 3.x
- **AI**：LangChain4j / Spring AI、Ollama、vLLM、BGE Embedding & Reranker
- **基础设施**：Redis、Kafka、Milvus / Qdrant、Prometheus + Grafana
- **商业化**：Stripe