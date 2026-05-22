# AI 后端学习路线 · 个人补习清单 v1.0

> **配套文档**：ROADMAP.md / ADR-001~004 / SETUP.md  
> **创建日期**：2026-05-08（M1 · Week 3）  
> **用途**：补齐基础知识 + 沉淀对话精华 + 长期跟踪学习进度  
> **下次更新**：每月底 + 每完成一个 ADR

---

## 一、使用说明

这份文档解决三件事：

1. 把项目里出现的概念按"现在要 / 后面要"分类，告诉你**学什么**
2. 给每个概念配学习资源，告诉你**去哪学**
3. 沉淀对话中产生的关键洞察，避免半年后忘干净

**配套使用方式**：
- 每周扫一遍当前阶段的 A/B 类，挑一个没掌握的深挖
- 写代码遇到陌生概念时回查
- 月度复盘时勾选已掌握项

---

## 二、进度回顾（截至 Week 3）

### Week 1（2026-04-30）· 立项与基线

- 文档体系搭建：ROADMAP 总览 + 跨境时间线 + 方向假设池（5 条候选）
- 环境基线：macOS + Homebrew + Git + JDK 21 + GitHub SSH（SETUP.md）
- 决策落地：
  - **ADR-001**：流式输出 → SSE + Spring MVC + SseEmitter（不上 WebFlux）
  - **ADR-002**：Spring Boot 版本 → 3.5.14（不追 4.0，保留 LLM 框架候选范围）
- 硬件评估、跨境时间线确认

### Week 2 · 选型收敛

- **ADR-003**：跨境部署 → 香港中转 + Stripe/微信支付分层
- **ADR-004**：LLM 框架 → LangChain4j 1.11.x + Spring Boot Starter

### Week 3（当前）· 第一次真写业务代码

- Token 计费中间件
- 请求/响应日志 `@Aspect`
- ChatMemoryStore 持久化

**整体观察**：前两周文档与选型质量很高，代码层面还薄。Week 3 是知识缺口集中暴露的开始。

---

## 三、学习方法论：AI vs 传统学习

### 直接结论：分层混合，不要 all-in 任何一种

| 学习目标 | 首选方式 | 辅助方式 | 原因 |
|---|---|---|---|
| **概念理解**（"X 是什么/为什么这样设计"） | **AI 对话** | 官方文档兜底 | AI 最强的就是定制化类比、"为什么"链条、连接已知 |
| **系统化 mental model**（一整个领域的全图） | **视频课 / 书** | AI 答疑 | AI 只能给你点状回答，全图需要别人先帮你画 |
| **编码肌肉记忆** | **自己敲，从空白文件开始** | 卡住才问 AI | 看懂 ≠ 会写。代码不是读出来的，是手敲出来的 |
| **调试** | **看真实日志和 stack trace** | AI 提供假说 | 你环境的 bug，AI 看不到上下文，常误导 |
| **读真实代码** | **GitHub 真实项目** | AI 解释难懂段落 | "看 LangChain4j 源码"和"听 AI 讲 LangChain4j"是两件事 |

### AI 学习的四个真实陷阱

**陷阱 1：跳过"卡住—挣扎—突破"的过程**  
学习的肌肉就长在"我卡了 30 分钟没解决"那 30 分钟里。AI 一秒答疑会让你跳过这一段，**你以为你懂了，其实是 AI 替你懂了**。
- ✅ 对策：对自己定个规矩——卡住先自己想 15 分钟、查文档 10 分钟，还不行再问 AI

**陷阱 2：版本/生态时效性**  
AI 训练数据可能落后半年到一年。LangChain4j 1.x 这种快速演进的框架，AI 给的代码有概率是过时 API。
- ✅ 对策：API 级问题永远以官方文档为准，AI 答案当"假说"而不是"答案"

**陷阱 3：没有反馈回路**  
AI 答错了不会被惩罚，你也很难发现答错了。看视频/读书时如果作者讲错，社区会喷他、你会看到评论修正。
- ✅ 对策：重要概念交叉验证 2-3 个来源（AI + 官方 + 社区博客）

**陷阱 4："懂了"幻觉**  
AI 会顺着你说话的方式给你正反馈，让你以为掌握了。其实只是 AI 把抽象的东西帮你包装得"像你已经懂了"。
- ✅ 对策：用费曼方法测试——能不能不看任何资料、只用嘴讲明白这个概念？讲不通就是没真懂

### 推荐的工作流

```
[新概念] 
    ↓
[找一份系统材料] (15-30 分钟视频 / 一篇官方 doc)
    ↓
[自己写一个最小 demo] (从空文件开始，不复制粘贴)
    ↓
[卡住了 → AI 答疑 + 类比]
    ↓
[demo 跑通 → 用费曼方法讲一遍]  ← 关键步骤别跳！
    ↓
[一周后回看，能讲出来吗？]
    ↓
[掌握 / 还没掌握 → 回到第二步]
```

---

## 四、知识地图

### A. Spring / Java —— Week 3 立刻要用

| 概念 | 一句话解释 | 优先级 |
|------|-----------|------|
| Servlet | Java 处理 HTTP 的底层规范，Tomcat 是它的实现 | ★★★ |
| Filter / Interceptor | 请求进 Controller 前后的两层"门卫"，前者在 Servlet 层、后者在 Spring MVC 层 | ★★★ |
| AOP / `@Aspect` / `@Around` | 面向切面编程，把日志/事务/计费这种横切关注点抽出来 | ★★★ |
| `@Transactional` | 声明式事务，方法抛异常自动回滚 | ★★ |
| JPA / Hibernate | Java 持久化标准 / 主流实现，对象 ↔ 数据库行映射 | ★★ |
| MDC | 给同一请求的所有日志打同一 requestId 的机制(本项目统一用 requestId,不用 traceId,见 CONTEXT.md) | ★★★ || IoC / DI | Spring 的核心机制，`@Autowired` 背后的原理 | ★★★ |
| Spring Boot Starter | 一组打包好的依赖 + 自动配置 | ★★ |
| Actuator / Micrometer | Spring 的健康检查 + 指标采集（M9 接 Prometheus 用） | ★ |

**学习资源**：

| 资源 | 适合 | 评价 |
|---|---|---|
| **Spring 官方 reference docs**（spring.io/projects/spring-boot） | 权威查询 | 不适合从零学但永远是 source of truth |
| **Baeldung**（baeldung.com） | 单点深挖 | 教 AOP、`@Transactional` 这种具体话题最强 |
| **廖雪峰 Java 教程**（liaoxuefeng.com） | 中文入门 | Java 核心 + Spring 全栈，免费、系统、新手友好 |
| **黑马程序员 Spring Boot**（B 站） | 视频党 | 国内最主流的 Spring 视频教程之一，质量稳定 |
| **《Spring 实战》第 6 版**（Craig Walls） | 系统化 | 经典 Spring 入门书，看完一遍能搭起完整 mental model |
| **《深入理解 Spring 框架》**（中文社区博客系列） | 进阶 | 等你写过几个项目后再看，理解 IoC/AOP 底层 |

**AI 提问模板**（高质量提问示范）：
- ❌ 差：「AOP 是什么」
- ✅ 好：「我在 Spring Boot 3.5 里写一个 `@Around` 拦截 Service 方法，拿到方法返回值后想根据返回值动态记录不同日志，举一个完整例子并解释每行」

---

### B. LangChain4j 与 LLM 编程 —— Week 2 起贯穿 M1-M2

| 概念 | 一句话解释 | 优先级 |
|------|-----------|------|
| Token / Prompt token / Completion token | LLM 的最小处理单位，输入和输出 token 计费分开 | ★★★ |
| Function Calling / Tool Calling | LLM 返回"我要调函数 X 加这些参数"的 JSON，你的代码执行后回填 | ★★★ |
| Function Schema | 用 JSON Schema 描述函数让 LLM "看" | ★★★ |
| Agent | LLM 在多轮 tool call 中自主决策下一步的编排模式 | ★★★ |
| `AiServices`（LangChain4j） | 注解式声明 Agent，框架替你做 schema 生成 + 多轮编排 | ★★★ |
| ChatMemory / ChatMemoryStore | 多轮对话上下文管理，Memory 是接口，Store 是持久化 | ★★★ |
| Context Window | 模型一次能"看见"的最大 token 数 | ★★ |
| Streaming / TokenStream | 边生成边推送，前端边收边渲染 | ★★★ |
| OpenAI-compatible API | 事实标准接口，写一次客户端打 OpenAI/Ollama/vLLM/国产大厂都行 | ★★ |
| `ChatModelListener`（LangChain4j SPI） | 框架原生的"切面"，专为计费/观测设计 | ★★★ |

**学习资源**：

| 资源 | 适合 | 评价 |
|---|---|---|
| **LangChain4j 官方文档**（docs.langchain4j.dev） | 唯一权威 | **本项目第一资源**，每个概念都有 quickstart |
| **LangChain4j GitHub examples**（github.com/langchain4j/langchain4j-examples） | 真实代码 | 至少把 streaming + tool calling + RAG 三个 example 跑通 |
| **OpenAI 官方 Function Calling guide** | 概念正典 | LangChain4j 的 tool calling 抽象就来自这套，看一次受益 |
| **Andrej Karpathy** "Let's build GPT" 系列（YouTube） | 底层理解 | 不是必看但能让你"理解 token 是什么"提升一个量级 |
| **AI 答疑** | 日常问题 | LangChain4j 1.x API 还在演进，AI 答案对照官方 doc 用 |

**避坑**：1.x 期间 API 改名很快（`StreamingResponseHandler` → `StreamingChatResponseHandler`），所有 AI 答案都要对照当前版本文档核一遍。

---

### C. Java 异步与并发 —— ADR-001 的核心理论

| 概念 | 一句话解释 | 优先级 |
|------|-----------|------|
| thread-per-request | 每个请求独占一线程到结束，Tomcat 默认模型 | ★★★ |
| Tomcat 默认 200 线程 | 线程池大小，超过即排队，可调 | ★★★ |
| Java 21 虚拟线程 | JVM 级"廉价线程"，能开几十万个 | ★★★ |
| M:N 调度 | M 虚拟线程跑在 N 载体线程上，IO 等待时虚拟线程被"卸下" | ★★★ |
| Pinning（钉死） | 虚拟线程进 `synchronized` 不能切走，Java 24 已修复 | ★★ |
| `ScopedValue` | 替代 ThreadLocal，虚拟线程友好 | ★★ |
| Event Loop | 少量线程跑大量任务靠回调切换（Node.js / Netty / Reactor 内核） | ★★ |
| Mono / Flux | Reactor 的 0-1 / 0-N 数据流 | ★ |
| Backpressure | 消费者反向告诉生产者"慢点"的机制 | ★ |
| `boundedElastic` | Reactor 跑阻塞调用的线程池 | ★ |

**学习资源**：

| 资源 | 适合 | 评价 |
|---|---|---|
| **JEP 444（Virtual Threads）** | 想读权威源头 | Brian Goetz 团队的设计文档，比任何博客都准 |
| **《Java Concurrency in Practice》**（Brian Goetz） | 系统化 | 经典并发编程书，但是 pre-virtual-threads 时代的 |
| **《深入理解 Java 虚拟机》第 3 版**（周志明） | 中文系统化 | 第 12-13 章讲线程与并发，国内 Java 党人手一本 |
| **Inside Java（YouTube/blog）** | 追新 | Oracle Java 团队的官方频道，虚拟线程相关视频质量高 |
| **Project Reactor 官方 docs** | WebFlux 兜底 | 你不会主用 Reactor，但概念扫一眼能听懂别人聊响应式 |

**省时建议**：响应式（Mono/Flux 那套）按 ADR-001 你不会主用，**只读概念不练代码**就够。把时间留给虚拟线程。

---

### D. 本地推理与部署 —— M3 之前预习就够

| 概念 | 一句话解释 |
|------|-----------|
| Ollama | 本地跑 LLM 最简单的工具，类似"LLM 版 Docker" |
| vLLM | 生产级推理引擎，吞吐量比 Ollama 高一个量级 |
| llama.cpp | C++ 推理引擎，Ollama 的底层之一 |
| PagedAttention | vLLM 的核心技术，借鉴 OS 虚拟内存分页管理 KV Cache |
| KV Cache | Transformer 推理时存历史 token 中间结果，占显存大头 |
| 量化 / INT8 / INT4 | 把权重从 FP16 压到 8/4 bit，显存减半再减半，精度损失通常可接受 |
| GGUF | llama.cpp 生态的量化模型格式（替代了 GGML） |
| tokens/s | 推理吞吐量单位 |
| Batch Inference | 多请求合并送 GPU，一次前向算完，吞吐量翻倍 |

**学习资源**：

| 资源 | 适合 | 评价 |
|---|---|---|
| **vLLM 官方 docs**（docs.vllm.ai） | 实操 | M3 启动时主用 |
| **PagedAttention 论文**（arxiv 2309.06180） | 想理解原理 | 不长，工程导向，可读 |
| **HuggingFace transformers 文档** | 量化扫盲 | bitsandbytes、GPTQ、AWQ 这些算法的入门 |
| **李沐"动手学深度学习" / paper reading**（B 站） | 中文深度 | 量化、Attention、KV Cache 这些都有他讲过的视频 |
| **Andrej Karpathy** "Let's reproduce GPT-2"（YouTube） | 终极理解 | 几小时长视频，看完对推理底层零障碍 |

---

### E. RAG 全链路 —— M6 之前预习就够

| 概念 | 一句话解释 |
|------|-----------|
| RAG | 检索 + 生成，对抗幻觉的主流方案 |
| Embedding | 把文本压成几百维向量，语义相近距离近 |
| 向量数据库 | 存 embedding 做相似度检索，Milvus / Qdrant / Pinecone |
| Chunking | 把长文档切成 LLM 能消化的小块 |
| Semantic Chunking | 按句子语义相似度动态切，不按字数 |
| Parent-Child Chunking | 检索用小块，喂 LLM 用大块 |
| BM25 | 关键词检索算法，和向量检索互补 |
| Hybrid Search | 向量 + BM25 并行 + 结果融合 |
| RRF | 多路结果按"排名倒数"加权融合的简单算法 |
| Reranker | 检索召回后的二次精排，BGE-Reranker-v2 是开源主流 |
| BGE / BGE-M3 | 北京智源开源的 embedding/reranker，中文场景强 |
| MRR@5 | 评估指标，关注"答案是否在前 5"，越接近 1 越好 |
| RAGAs | 自动评估 RAG 的开源框架 |
| GraphRAG | 用实体关系图辅助检索，微软 2024 提出 |

**学习资源**：

| 资源 | 适合 | 评价 |
|---|---|---|
| **LangChain4j RAG docs** | 框架视角 | 入门把整条链路串起来 |
| **Pinecone Learning Center**（pinecone.io/learn） | 概念图解 | 向量检索、chunking、reranker 都有图解文章 |
| **BGE 模型卡（HuggingFace）** | 中文场景必看 | 模型卡 + 评测榜直接看清"为什么 BGE 是中文 RAG 主流" |
| **RAGAs 官方 docs** | 评估 | M8 阶段直接照着做 |
| **王树森 RAG 系列**（B 站） | 中文系统化 | 国内讲 RAG 比较系统的免费视频之一 |
| **原始 RAG 论文**（arxiv 2005.11401） | 历史 | 知道源头，看一次就好 |

---

### F. 基础设施 —— M9 之前知道有这些东西就行

| 概念 | 一句话解释 |
|------|-----------|
| Redis 分布式锁 | 跨进程互斥锁 |
| Kafka | 分布式消息队列，耗时任务异步化 |
| 多租户 | 一套系统服务多客户，数据隔离（独立库 / 独立 Schema / 共享表加 tenant_id） |
| Prometheus + Grafana | 标准监控栈：拉指标 + 出图表 |
| OpenTelemetry | 可观测性统一标准 |
| 灰度发布 | 新版本先放小流量观察 |

**学习资源**：

- **Redis 官方文档** + **《Redis 设计与实现》黄健宏**（国人写的最好的 Redis 中文书）
- **Kafka 官方 quickstart** + **《Kafka 权威指南》**
- **Prometheus 官方 tutorial**（半天能跑起来）

到 M9 再深入即可。

---

### G. 跨境与商业化 —— ADR-003 出现的术语

| 概念 | 一句话解释 |
|------|-----------|
| ICP 备案 | 中国大陆服务器对外提供网站服务的强制许可，要营业执照 |
| CN2 GIA | 电信优化国际线路，香港 ↔ 大陆延迟低 |
| PIPL | 中国个人信息保护法，用户数据出境超门槛要申报 |
| Stripe Webhook | Stripe 用 HTTP 回调通知付款状态，需验签 |
| 订阅状态机 | 试用 → 活跃 → 逾期 → 暂停 → 注销 |
| MRR | 月度经常性收入 |
| DAU / 留存率 | 日活 / 用户回访比例 |

**学习资源**：等 M11 启动前再深入，**Stripe 官方 docs** 是唯一必读。

---

### H. 项目里反复出现的"行话"（速查表）

| 缩写 | 全称 | 含义 |
|------|------|------|
| ADR | Architecture Decision Record | 架构决策记录 |
| LTS | Long Term Support | 长期支持版本 |
| GA | General Availability | 正式发布版 |
| DX | Developer Experience | 开发者体验 |
| OSS | Open Source Software | 开源软件 |
| SDK | Software Development Kit | 调用某服务的代码库 |
| SSO / OAuth | 单点登录 / 授权协议 | GitHub 登录第三方网站走的协议 |
| CDN | Content Delivery Network | 内容分发网络 |
| JVM | Java Virtual Machine | Java 虚拟机 |
| SPI | Service Provider Interface | 服务提供者接口（框架扩展点机制） |
| MDC | Mapped Diagnostic Context | 日志诊断上下文（traceId 容器） |
| DI / IoC | Dependency Injection / Inversion of Control | 依赖注入 / 控制反转 |
| AOP | Aspect-Oriented Programming | 面向切面编程 |
| QPS | Queries Per Second | 每秒请求数 |
| P50 / P99 | 50th / 99th percentile latency | 中位数 / 99 分位延迟 |

---

## 五、关键深度笔记（对话沉淀）

> 这些是 Week 3 对话中沉淀下来的"想清楚了的"东西。半年后回看也能马上唤起。

### 笔记 1：50 并发 vs Tomcat 200 线程 vs 响应式

**核心区分**：
- 总用户数 ≠ QPS ≠ 并发数
- "50 并发" = 任意瞬间有 50 个用户正在用系统（餐厅里同时吃饭的人数）
- SSE 是长连接，一次对话 5-30 秒，所以 50 并发 = 50 个 HTTP 连接挂着

**Tomcat 200 线程的来历**：
- Java 传统线程 = OS 线程 1:1，每个吃 ~1MB 内存
- 200 是"够用且不浪费"的甜蜜点，调大 = 多吃内存

**ADR-001 决策的真实逻辑**：
- 50 并发 < 200 线程 = 舒适区 4 倍
- WebFlux 优势在 5000+ 长连接 / 纯 IO 等待场景，本项目用不到
- **WebFlux 真正的代价不是性能，是思维反转 + 调试痛苦 + 传染性陷阱**

### 笔记 2：虚拟线程的 M:N 调度

**核心思想**：把"任务"和"执行任务的线程"解耦。

**机制**：
- 载体线程（Carrier Thread）：真 OS 线程，少（~CPU 核数）
- 虚拟线程：纯 Java 对象，多（百万级）
- 阻塞 IO 时 JVM 把虚拟线程的栈打包存堆，载体线程释放去跑别的

**咖啡店比喻**：
- 传统：200 个咖啡师，每人一工位，等咖啡机的 5 秒啥也不干
- 虚拟：8 个咖啡师 + 10 万订单。咖啡师启动机器后立刻接下一单，机器响了空闲咖啡师过去端

**对本项目的意义**：
- 现在 50 并发 → 50 个 Tomcat 线程 → 50MB
- 开虚拟线程后 → 50 个虚拟线程，载体可能就 1-2 个 → 几百 KB
- **配置：`spring.threads.virtual.enabled=true`** 一行搞定

**陷阱**：
- CPU 密集任务无收益（咖啡机就是慢）
- `synchronized` pinning（Java 21 有，Java 24 修复）→ 现阶段用 `ReentrantLock` 替代
- ThreadLocal 变内存炸弹 → Java 21 引入 `ScopedValue` 替代

**何时启用**：**M5 压测阶段做正式实验，写 ADR-005**。M1 阶段不开（避免业务 bug 和虚拟线程坑混在一起）。

### 笔记 3:@Aspect 在 SSE 流式场景的"错位"

**反直觉的事实**:AOP **不适合**直接数 token(此处 token 指模型层的 prompt/completion token,
不是 SSE 推送的 chunk,详见 CONTEXT.md)。

**原因**:SSE 的 controller 方法 ~1ms 就返回了(只是抛出 SseEmitter),真正的 chunk 推送
发生在方法返回**之后**。`@Around` 拦截不到那部分,因此也拿不到流式完成时才齐全的 TokenUsage。

```
T=0      Controller 返回 SseEmitter           ← @Around 在这里"结束"
T=0.001  方法返回,AOP 切面退出
T=0~5    LLM 流式推送 chunk(几秒钟)
T=5      onComplete 触发,TokenUsage 此时才有  ← AOP 已经看不到
```

### 笔记 4:Token 计数的正确架构

**分工清晰**(权威表见 ADR-006,此处为简版):

| 关注点 | 用什么 |
|------|------|
| requestId / userId 注入 | Filter / Interceptor |
| 请求开始/结束 timing | `@Aspect @Around` |
| **Token 计数**(模型层) | **`ChatModelListener`(LangChain4j SPI)** |
| 流式 chunk 推送 | `StreamingChatResponseHandler.onPartialResponse` |
| 流式完成时拿 TokenUsage | `onCompleteResponse(ChatResponse)` |
| 失败也要记账 | `onError` |

**LangChain4j 官方做法**:实现 `ChatModelListener`,spring-boot-starter 自动把 `@Component`
类型为 `ChatModelListener` 的 Bean 注入到所有 ChatModel,零配置(流式和非流式都生效)。

**跨线程上下文传递的双轨**(详见 ADR-006):
- **同步链路 + 日志输出**:MDC 装 requestId,Filter 注入,@Aspect 读取
- **跨线程的 Listener 内部**:`ChatModelRequestContext.attributes()` 装 requestId,
  onRequest 塞、onResponse / onError 取

**MDC 暗坑**:流式回调不在 AOP 同一线程时刻,MDC 会丢。如果需要在流式回调里输出带 requestId
的日志,要 snapshot MDC 然后传到回调里再装载:

```java
Map<String, String> mdcSnapshot = MDC.getCopyOfContextMap();
emitter.onCompletion(() -> MDC.clear());
emitter.onTimeout(() -> MDC.clear());
chatService.streamChat(prompt, mdcSnapshot, emitter);
```

### 笔记 5:故障排查的层级纪律

排查"明明不该出错的事情却出错"的层级(由近及远):

1. 业务代码(95% 的概念性 bug 在这层)
2. 框架配置(application.yml、环境变量)
3. 框架版本(stable vs beta,版本对齐)← 今天的根因
4. JVM 参数(VM options、system properties)
5. 构建工具(IntelliJ vs mvn 命令行)
6. JDK 版本(LTS vs 非 LTS)
7. OS 网络栈(代理、DNS、socket)
8. 物理网络(curl 直接测连通性)

诊断技巧:从远端往近端逐层排除,**每次只动一个变量**。

### 笔记 6:跨线程回调的认知冲突 —— 我以为同步,实际上是 ForkJoinPool

**触发点**:Day 3 BillingListener 三回调骨架跑通后,第一次观察到 `Thread.currentThread().getName()` 的真实输出:

```
[BillingListener] onRequest  | thread=main
[BillingListener] onError    | thread=LangChain4j-OpenAI-1   ← 完全不同的线程
```

(后来切到 OpenAI starter 后变成 `ForkJoinPool.commonPool-worker-1`,机制相同。)

**认知冲突在哪**:

我下意识以为 ChatModelListener 的三个回调跑在**调用线程**——你 controller 进来是 Tomcat handler,Listener 在同一个线程上挨个回调。这套心智模型在**非流式**调用里是对的:`chatModel.chat(...)` 同步阻塞,onRequest → 真实 HTTP → onResponse 全在一个线程里串完。

但**流式**调用完全不同。`streamingChatModel.chat(...)` 立刻返回(因为它是 fire-and-forget),真实 HTTP 由 LangChain4j 的 `JdkHttpClient.sendAsync` 异步发出,**回调注册到 CompletableFuture 上,由 ForkJoinPool 派发**。所以 onRequest 还在主线程跑(此时 sendAsync 还没真启动),但 onResponse / onError 一定在某个 worker 线程上跑。

**为什么这个认知冲突很重要**:

它直接决定了"上下文怎么跨阶段传"。我本能想用 MDC(因为之前所有 Spring 项目都用 MDC),但 MDC 是 ThreadLocal,**跨线程必丢**。Day 3 当时就观察到这一现象——如果在 onRequest 里 `MDC.put("requestId", ...)`,onError 里 `MDC.get` 拿到的是 null(或者更糟,拿到上一个跑在同一 ForkJoinPool worker 上的请求的脏数据)。

LangChain4j 把这个问题考虑过,所以三个 context(`ChatModelRequestContext` / `ResponseContext` / `ErrorContext`)都有一个 `attributes()` 方法,返回**同一个 ConcurrentHashMap 实例**,生命周期与一次 chat() 调用对齐。`onRequest` put 进去,`onResponse` / `onError` get 出来,跨线程安全。这是笔记 4 "Token 计数的正确架构" + ADR-006 "双轨上下文传递" 的真正落脚点。

**给自己的提醒**:

下次接触任何"流式 / 异步 / 回调式"框架时,**第一步打 `Thread.currentThread().getName()` 看回调跑在哪**。心智模型里的"同一线程"可能是错的。

### 笔记 7:Day 3→4 卡点 —— 锁定为 JDK 21 异步路径暗坑(后被笔记 8 推翻)

> ⚠️ **2026-05-20 修正**:本笔记的核心假设("JDK 21 异步路径本身是暗坑")已被 Day 4 末的反证链推翻,真正变量是 IDEA Test Runner 的 agent 注入,与 JDK 本身无关。详见笔记 8。**本笔记保留原文,作为"诊断失误样本",不修改**。

---

**症状**:Day 3 BillingListener 骨架跑通后,所有 `BillingListenerSmokeTest` 真实 LLM 流式调用都抛 `UnresolvedAddressException`,**35ms 失败,不出网络**。

```
Caused by: java.nio.channels.UnresolvedAddressException
    at sun.nio.ch.Net.checkAddress
    at jdk.internal.net.http.PlainHttpConnection.lambda$connectAsync$1
    ...
```

**排查过程**(按笔记 5 的 7 步层级,由近到远):

| 层级 | 尝试 | 结果 |
|------|------|------|
| 业务代码 | review SmokeTest 多次 | 无问题 |
| 框架配置 | `application.yml` 检查 | 无问题 |
| 框架版本 | LangChain4j 1.11.0 core + 1.11.0-beta19 spring 双轨锁定 | 同样复现 |
| JVM 参数 | 试过加 `-Djava.net.preferIPv4Stack=true` 等 | 无效 |
| 构建工具 | `mvn test` 命令行复现 | 同样复现(后来发现这一步可能有误,见笔记 8) |
| JDK 版本 | 21.0.11 → 21.0.10 patch 切换 | 同样复现 |
| OS 网络栈 | `curl https://api.openai.com/v1/...` 直接打 | **通**,说明不是网络层 |
| 物理网络 | wifi 切换、热点、地理位置切换 | 同样复现 |

**推理**:8 步全过都不能解释。剩余未排除的假设池里,只剩"JDK HttpClient 异步路径本身有 bug"。同步 send() 测试 GET 通,异步 sendAsync POST + SSE 失败,这个对比似乎佐证了"异步路径有暗坑"的判断。

**锁定结论**:JDK 21 macOS HttpClient sendAsync 暗坑(LangChain4j 1.11 用 `langchain4j-http-client-jdk` 实现,内部就是 sendAsync)。

**沙盒化策略**:

- 不再追这个 bug(继续追估计也追不出,而且已经花了一周)
- 把 SUCCESS 路径用 `@Disabled` 占位,Day 4 继续推进 Step 2 / 4 / 5
- onError 路径反而成了"现成的真实异常样本数据源",喂给 BillingStatusClassifier 做单测

**给未来的我**:

- 这条结论我不是 100% 有把握。8 步全过的反推法在统计学上是合理的,但"假设池完整性"无法被反推法本身证伪。
- 如果 Day 5 之后 SUCCESS 路径还需要跑,优先尝试:升级 LangChain4j 1.12+(可能改了 http client 实现)、换 `langchain4j-http-client-spring-restclient`(已经在 pom 里)、或者换 OkHttp client。
- **不要再花时间追"JDK bug 本身"的根因**。投入产出比太低,沙盒化继续推进业务管道更重要。

---

**👆 以上是原笔记 7 的全部内容。下面是 2026-05-20 修正的简短说明**:

笔记 8 描述的反证链(三次 curl 验证)表明:同一台 Mac、同一份代码、同一个 JDK 21,只要用 `spring-boot:run` 启动而不是 IDEA Test Runner,就完全没有 `UnresolvedAddressException`。**真正变量是运行方式而非 JDK 本身**。

但本笔记**保留原文不删**,作为"锁定根因前漏掉一个变量"的诊断纪律样本。半年后回看,这个错误本身比正确答案更有教育意义。

### 笔记 8:诊断纪律失误复盘 —— 把"环境 bug"误锁成"JDK bug"

**关键背景**:笔记 7 锁定的"JDK 21 macOS HttpClient sendAsync 暗坑"这一假设,在 Day 4 末被三次 curl 形成的反证链推翻。真正的根因没有彻底定位(可能也不打算彻底定位),但**锁定假设这件事本身就是诊断失误**。这条笔记记录这一失误的形成与拆除过程,作为"诊断纪律"的反例样本。

#### 事实链(时间轴)

| 日期 | 事件 | 结果 |
|------|------|------|
| 2026-05-15 Day 3 | BillingListener 骨架完成,SmokeTest 跑出 `UnresolvedAddressException`,35ms 失败,不出网络 | 卡点出现 |
| Day 3 → Day 4 | 一周排查:网络 / 代理 / JDK 版本 / HTTP 版本 / Proxy / LangChain4j adapter 全部排除 | 假设收敛到"JDK 异步路径暗坑" |
| Day 4 中段 | 写笔记 7、沙盒化 SUCCESS 路径(`@Disabled` 占位)、继续推进 Step 2/4/5 | 工程进度未停滞,但根因未解 |
| Day 4 末 (15:32) | **第一次 curl**:用 `spring-boot:run` 启动 + invalid key → HTTP 401 `AuthenticationException` → ERROR_RESPONSE 落库 | **网络是通的**,根本不存在 DNS 解析失败 |
| Day 4 末 (15:38) | **第二次 curl**:换 valid OpenAI key → HTTP 200 → SUCCESS 路径触发,拿到 `tokenUsage` | Step 3 一周的"盲点"瞬间解锁 |
| Day 4 末 (15:38) | **第三次 curl**:补完 Step 3 落库代码 → BillingLog id=2 落库 status=SUCCESS latencyMs=1067 | Day 4 五步全过 |

#### 笔记 7 假设是怎么锁定的

排查走的是教科书式的"由近到远"层级(笔记 5):
- ✅ 业务代码:多次 review,无明显问题
- ✅ 框架配置:`application.yml` 检查无误
- ✅ 框架版本:LangChain4j 1.11.0 + spring 1.11.0-beta19 双轨锁定
- ✅ JVM 参数:无可疑
- ✅ 构建工具:Maven 命令行也复现
- ✅ JDK 版本:升降级 patch 版试过
- ✅ OS 网络栈:curl 直接打 OpenAI 通,排除代理 / DNS
- ✅ 物理网络:wifi 切换、热点、不同地理位置都试

排到第 8 步,八步全过却仍然复现,**剩下的"未被排除"假设池只剩"JDK 异步路径本身"**。于是写下笔记 7,沙盒化,继续推进。

**这个推理过程逻辑上无懈可击,但漏了一个变量**。

#### 反证证据(Day 4 末 30 分钟内形成)

三次 curl 跑出来的对比:

| 运行方式 | API Key 状态 | Base URL | 结果 |
|----------|--------------|----------|------|
| **IDEA Test Runner**(SmokeTest)| invalid(DeepSeek 配到 OpenAI 路径)| api.openai.com | `UnresolvedAddressException` 35ms |
| **`spring-boot:run`** | invalid(同上)| api.openai.com | HTTP 401 `AuthenticationException` 800ms |
| **`spring-boot:run`** | valid | api.openai.com | HTTP 200 SUCCESS 1067ms |

同一台 Mac、同一份代码、同一个 JDK 21、同一个 IDEA 进程父。**唯一变量是运行方式**(IDEA Test Runner vs `spring-boot:run`)。

#### 错在哪里

排查的 8 步层级里,**没有 "运行方式" 这一层**。IDEA Test Runner 在 JVM classpath 里注入了多个 agent(`debugger-agent.jar` / `byte-buddy-agent.jar` / `idea_rt.jar`),`spring-boot:run` 没有这些。这些 agent 对 JDK HttpClient 异步路径的具体影响**今天未深究**,但客观事实是:**它们是变量**,而笔记 7 把它们当成了不变量(实际上根本没意识到它们存在)。

锁定"JDK bug"那一刻,**关闭的不是"再排查一层"的可能性,而是"换运行方式试试"这扇本来该开着的门**。门一关,SUCCESS 路径就再也不会被触发,直到 Day 4 末因为别的事情(Step 5 验证)启动 `spring-boot:run`,门才被无意中重新推开。

#### 真正的教训

1. **"假设池排除完了"≠"真因已定位"**。每一步排查实际上都在缩小假设池,但**假设池本身的完整性是有边界的**——你只能排除你想到的假设。"运行方式"这种系统性偏见,排除多少层也排除不到。
2. **锁定根因前永远问一句:"我换一个我没换过的变量再试一遍了吗?"** 即便那个变量看起来"不可能相关"。Day 4 末发现网络是通的那一刻,如果三天前就用 `spring-boot:run` 跑一次,卡点就不存在。
3. **写笔记 7 时的"已排除清单"应该是开放式而非封闭式**。"已排除 X / Y / Z" → "**目前已尝试** X / Y / Z,未尝试 A / B / C"。后者保留了重新审视的入口。
4. **沙盒化策略本身是对的**。笔记 7 即便假设错了,沙盒化让 Day 4 的其他四步照常推进,代价被局部化了。这一点不该被"假设错了"的复盘抹掉——**好的工程纪律可以让坏的判断不至于灾难**。

#### 给排查清单(笔记 5)的补丁

在原有 8 步之上加第 0 步:

> **0. 运行环境差异**(应该最先怀疑,但常被忽略)
> 同一份代码在不同运行方式下表现不同 → 99% 是环境问题。具体变量:IDE 测试运行器 vs `mvn` 命令行 vs `spring-boot:run`、profile 差异、classpath 差异、agent 注入差异。

#### 笔记 7 的处理

**不删**,但加一行修正注释指向本笔记:

> ⚠️ **2026-05-20 修正**:本笔记假设的"JDK 21 异步路径暗坑"已被 Day 4 末三次 curl 形成的反证链推翻。真正变量是 IDEA Test Runner 的 agent 注入,与 JDK 本身无关。详见笔记 8。本笔记保留作为"诊断失误样本",不修改原文。

### 笔记 9:Provider 切换的零代码实证 —— ADR-004 关键卖点的意外验证

**背景**:ADR-004 选 LangChain4j 时押的核心卖点之一,是"换 Provider / 换模型不应改业务代码"。M2 原计划是这个卖点的正式实验,但 Day 4 末因为 API key 配错(把 DeepSeek key 配到了默认指向 OpenAI 的环境变量上),**意外地实证了这一抽象的真实价值**。

#### 关键设计:application.yml 三个占位符

```yaml
langchain4j:
  open-ai:
    streaming-chat-model:
      base-url: ${LLM_BASE_URL:https://api.deepseek.com/v1}
      api-key: ${LLM_API_KEY}
      model-name: ${LLM_MODEL_NAME:deepseek-chat}
```

业务代码(BillingListener / ChatController / Aspect)**完全不知道**自己在打 OpenAI 还是 DeepSeek。Provider 由三个环境变量在运行时决定。

#### Day 4 末的实证

| 环境变量配置 | 实际打到的 Provider | 业务代码改动 |
|--------------|---------------------|--------------|
| `LLM_BASE_URL=https://api.openai.com/v1` + `LLM_MODEL_NAME=gpt-4o-mini` + valid OpenAI key | api.openai.com | **零行改动** |
| 不设(走默认)+ valid DeepSeek key | api.deepseek.com | **零行改动** |

BillingListener 在两个 Provider 下都正确触发了 onRequest / onResponse / onError 三个回调。TokenUsage 抽象一致(LangChain4j 层做的适配),Token 计费逻辑无需关心 Provider 差异。

#### 与 ADR-004 的关系

ADR-004 "实际效果(事后补充)"原本留给 M2:
> M2 结束时回填:换 Provider 时业务代码改动量是否真的为零?

**Day 4 已经回答了"是的,零改动"**。M2 正式实验时要在此基础上补的:
- 单价表:不同 Provider 的 input / output 单价(目前都是 0)
- BillingLog.provider 字段动态化(目前硬编码 "openai")
- 切到 Ollama 本地模型时的延迟 / 吞吐对比

#### 这个意外的元价值

这种"我已经把它做了但当时没意识到"的经验,是 ADR 设计良好的真实证据。如果架构是脆的,换 Provider 就该崩了——但没崩,说明抽象层切对了。这一点比 SUCCESS 路径落库本身**更值钱**,因为它验证的是设计意图,不是单一功能。

### 笔记 10:重命名时的"清单漏项"陷阱(笔记 8 主题的微缩复现)

**触发点**:Day 5 收尾做 sessionId → conversationId 全局重命名时,**同一天连续遇到两次同模式失误**——以为变更已经做完,实际有漏项,被编译错误/测试失败暴露。

#### 失误 1:列 Step 5b 改动清单时漏掉 BillingLogRepositoryTest.java

**事实**:做 BillingLog 持久化层重命名时,列改动文件清单只列了 4 个:
- BillingLog.java(字段)
- V3 SQL migration(列名)
- BillingLogSchemaTest.java(列名常量)
- BillingListener.java(后来发现根本没引用 sessionId,实际不动)

**遗漏**:`BillingLogRepositoryTest.java` 第 31 行 `log.setSessionId(...)` 和第 66 行 `getSessionId()`——Day 2 写 BillingLog 时配套的 Repository 测试,本来就应该列入但是凭"记忆 + 上下文"漏了。

**暴露方式**:Build → Build Project 报错 `cannot find symbol method setSessionId(java.lang.String)`,精确指向第 31 / 66 行。

**根因**:列清单时凭"我记得改过/读过哪些文件"猜,**没用 IDE 全文搜 sessionId 建立客观清单**。

#### 失误 2:Cmd+R 替换 sess-xyz-456 时只换了一处

**事实**:修第一次失误时,在 BillingLogRepositoryTest.java 里 Cmd+R 替换 `sess-xyz-456` → `conv-xyz-456`,只覆盖了第 66 行(断言里的 expected),漏了第 31 行(setConversationId 调用里的字符串)。

**暴露方式**:测试 `expected: "conv-xyz-456" but was: "sess-xyz-456"`——insert 时写了旧字符串,select 出来还是旧字符串,但断言期望新字符串。

**根因**:Cmd+R 默认 click 的是 **Replace**(单次替换当前匹配),不是 **Replace All**。手快 click 了 Replace 一次,看到第一处替换成功就以为完事,实际还有一处。

#### 共同模式

两次都是"**全局变更时,以为已经完成,但实际有遗漏**":
- 失误 1:漏了一个**文件**
- 失误 2:漏了一处**字符串**

两者都是"清单不完整"在不同尺度的版本。这正是笔记 8 主题(诊断纪律失误)的微缩版——笔记 8 是大尺度"锁错根因",笔记 10 是小尺度"清单建不全"。

#### 给笔记 5 / 笔记 8 的补丁

笔记 8 已经在排查清单里加了"第 0 步:运行环境差异"。笔记 10 加的是**全局变更前置纪律**(适用于 rename、refactor、删字段等所有"语义全局变更"):

**前置纪律 P0**:**动手前先用 IDE 全文搜建立客观清单**,不要凭"我记得改了哪些 / 直觉认为还涉及哪些"猜。

具体操作:
1. `Cmd + Shift + F`(IDEA Find in Files)搜旧关键词,Scope = Project,无文件后缀过滤(全语言扫:`*.java` / `*.sql` / `*.yml` / `*.md` 全覆盖)
2. 看搜索结果分布——区分"真要改"vs"javadoc/SQL 注释里的历史说明"
3. 真要改的统一 review 一次替换,**不要分批 / 不要凭记忆补丁**
4. 字符串字面值替换时——用 **Replace All**,不要按 Replace 单次;按完后**再搜一次确认归零**
5. 编译 + 跑测试,**任何意料之外的红/绿都警觉**

#### 元教训:与笔记 8 同源的认知偏见

笔记 8 写过:
> "已尝试 X / Y / Z"(开放式)而非"我已经检查完了"(封闭式)

笔记 10 是同一原则在"重命名场景"的具体化——**人脑做"已变更项清单"时,默认是封闭式("我以为我都改完了"),而不是开放式("还有什么我没改的?")**。IDE 全文搜的价值就在于把这个清单从"主观封闭式"翻译成"客观开放式"。

#### 给未来的我

下次任何"全局变更"启动前默念一句:**"Cmd + Shift + F 跑一下,确认完整性。"**

成本:1 秒。  
收益:消除"以为做完了"的二次返工(本次两次返工合计约 15 分钟,在远程协作 / 复杂代码库场景下成本会放大到几小时)。

**配套元纪律**:这种"小事故都进 LEARNING-NOTES + ADR 反向引用 LEARNING-NOTES"的工程纪律值得长期保留。ADR-007 实际效果 #5 已经反向链接到本笔记,形成"决策记录 ↔ 经验沉淀"的双向闭环。


## 六、进度追踪表

按优先级排，✅ 表示已掌握（能用费曼方法讲明白）。

### 现在就要（M1）

- [ ] Servlet 是什么，Tomcat 怎么处理一个请求
- [ ] Filter vs Interceptor 区别
- [ ] AOP / `@Around` / `@Before` 写一个 demo
- [ ] `@Transactional` 的传播行为（PROPAGATION_REQUIRED 等）
- [ ] MDC 在普通请求 + 异步任务里的用法
- [ ] Spring IoC 容器启动过程粗略理解
- [ ] LangChain4j AiServices 注解用法
- [ ] LangChain4j ChatMemory + ChatMemoryStore 接口
- [ ] LangChain4j ChatModelListener SPI
- [ ] Token 计数用 listener 而不是 AOP（笔记 4）

### M2 启动前

- [ ] LangChain4j 切换 Provider 实操（OpenAI → 国产大模型 / Ollama）
- [ ] OpenAI-compatible API 协议
- [ ] Tool Calling 多轮编排手动实现一次（不用 AiServices）

### M3 启动前

- [ ] Ollama 本地起一个 Qwen2.5-7B
- [ ] vLLM 启动 + OpenAI-compatible 端点
- [ ] PagedAttention 论文摘要级理解
- [ ] KV Cache 是什么，为什么占显存大头
- [ ] FP16 / INT8 / INT4 量化的精度-速度-显存三角

### M5 启动前

- [ ] **写 ADR-005：虚拟线程对照实验**（关键里程碑）
- [ ] Tomcat 线程池调优参数
- [ ] JMeter / wrk 压测工具用法

### M6-M8

- [ ] PDF 解析（PyMuPDF / Unstructured）
- [ ] Embedding 模型选型（BGE-M3 vs 商业 API）
- [ ] Milvus vs Qdrant 选型实操（写 ADR）
- [ ] BM25 + 向量 + RRF 写一遍
- [ ] BGE-Reranker-v2 接入
- [ ] RAGAs 跑一次评估

### M9-M12

- [ ] Redis 分布式锁实现细节（Redlock 争议）
- [ ] Kafka 消费者组、分区分配
- [ ] 多租户 Schema 级隔离 PoC
- [ ] Stripe 订阅扣费 + Webhook 验签
- [ ] Prometheus + Grafana 接入

---

## 版本历史

| 版本 | 日期 | 修订 |
|---|---|---|
| v1.0 | 2026-05-08 | 初版：Week 3 对话沉淀，A-H 八类知识地图，进度追踪 |
