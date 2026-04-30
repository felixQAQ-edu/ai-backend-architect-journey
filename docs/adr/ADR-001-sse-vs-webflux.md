# ADR-001 · Spring Boot LLM 流式输出技术选型（SSE vs WebFlux）

- **日期**：2026-04-30
- **状态**：已采纳
- **决策者**：Felix

## 背景

M1 阶段需要在 Spring Boot 后端打通"用户提问 → LLM 流式生成 → 前端逐 token 渲染"的全链路。

约束条件：

1. 单机预计承载并发量 ≤ 50（M5 压测目标也是 50 并发，量级一致）
2. 当前是学习计划第一周，需要把精力集中在"管道打通 + Tool Calling + 计费中间件"等业务主线，调试体验越接近传统 Spring MVC 越好
3. 数据流是单向的（服务端 → 客户端逐 token 推送），不需要双向通信
4. 后端会调用阻塞式的 LLM SDK / OpenAI-compatible HTTP 客户端，并不是端到端响应式栈

## 候选方案

### 方案 1：SSE + Spring MVC（SseEmitter）

**优点**：
- 同步阻塞编程模型，断点调试、stack trace、`@Transactional` 行为都是熟悉的 MVC 语义
- `SseEmitter` API 简单直接：new + send() + complete()，一个下午能跑通
- 与现有 Servlet 生态（Filter / Interceptor / Spring Security）无缝兼容，Token 计费中间件、请求日志落库这些 M1 任务可以直接用拦截器实现
- 浏览器原生支持 EventSource，前端一行代码接入
- 升级路径平滑：未来若并发压力上来，`application.yml` 加一行 `spring.threads.virtual.enabled=true` 即可获得接近响应式的吞吐量，业务代码零改动

**缺点**：
- thread-per-request 模型，每个 SSE 连接占一个 Servlet 线程
- 在 Tomcat 默认 200 max-threads 下，理论并发上限约 200，远超本项目 50 的需求，但确实不是为"5 万 SSE 长连接"这种场景设计的

### 方案 2：WebFlux + Flux<ServerSentEvent>（Reactor）

**优点**：
- 事件循环架构，少量线程支撑海量长连接，是 Netty + 响应式栈的"亲儿子"用法
- Backpressure 原生支持，能从消费端反向控制生产速度
- 对真正的全栈响应式（R2DBC + Reactive Redis + Reactive Kafka）项目是最优解

**缺点**：
- 学习曲线陡：Mono / Flux / Schedulers / Context 传播都不是入门级概念，M1 第一周引入会显著拖慢主线进度
- 调试体验差：stack trace 被 Reactor 操作符切碎，断点常常落不到预期位置
- 阻塞式 LLM SDK 必须手动包到 `boundedElastic` 调度器，否则会阻塞事件循环线程 —— 这一步出错就退化成"假响应式"，性能反而比 MVC 还差
- JPA / `@Transactional` 与响应式不兼容，后续若引入关系数据库会有撕裂感
- 对 50 并发的场景属于明显过度设计

### 方案 3：WebSocket

**优点**：双向全双工通信

**缺点**：本场景只需服务端单向推送，WebSocket 增加握手协议、心跳维护、断线重连等额外复杂度，属于明显过度设计。**排除。**

## 最终决策

**方案 1 — SSE + Spring MVC（SseEmitter）**

### 关键理由

- **匹配阶段目标**：M1 的核心是"管道优先"，技术选型应服务于"快速跑通全链路"，而不是引入新的认知负担。WebFlux 在 50 并发场景下的性能优势在本项目里观察不到，但其复杂度成本是确实要支付的。
- **匹配并发量级**：50 并发对 thread-per-request 模型完全是舒适区，Tomcat 默认配置即可，无需任何调优。
- **调试体验与生态兼容**：SseEmitter 走的是标准 Servlet 异步分支，所有 Spring MVC 的拦截器、AOP、`@Transactional`、MDC 日志都正常工作，M1 的 Token 计费中间件、请求日志落库可以直接复用熟悉模式。
- **保留升级路径**：如果未来真的需要支撑万级长连接（极不可能在本项目发生），第一选择是开启 Java 21 虚拟线程而非重写为 WebFlux —— 业务代码无需改动，单行配置即可获得接近响应式的吞吐量。

## 已知代价

1. 放弃了 Reactor 的 backpressure 能力。LLM 流式场景里这个能力的实际价值有限（token 推送速率由模型本身决定，前端处理速度通常不是瓶颈），可接受。
2. 放弃了响应式栈的"心智一致性"。如果后续 M9–M10 要引入 Reactive Kafka / R2DBC，需要重新审视这个决策。届时若决定切换，预计改造点：Controller 层重写（约 3 个端点）、调用链改用 Mono/Flux、阻塞 SDK 包到 Schedulers。

## 重新审视的触发条件

- 实测并发稳定超过 200，或
- 整个技术栈已演进为全响应式（关系库换 R2DBC、缓存换 Reactive Redis），或
- SSE 长连接数量级达到 5000+

## 实际效果（事后补充）

_M1 结束时回填：实际跑通用了多久？调试体验是否符合预期？是否遇到 SseEmitter 的坑？_