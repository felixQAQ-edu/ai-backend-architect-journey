# ADR-006 · @Aspect 与 ChatModelListener 的职责划分

- **日期**:2026-05-15
- **状态**:已采纳
- **决策者**:Felix

## 背景

M1 Week 3 Day 3 启动 Token 计费中间件落地。ROADMAP Week 3 任务清单原本写的是「Token 计费 `@Aspect` 中间件」(Week 1 草拟时的措辞),但实际推进时发现这一前提与 SSE 流式场景的事实不符。

**关键事实(LEARNING-NOTES 笔记 3 已沉淀)**:

ADR-001 选定 SSE + SseEmitter 后,Controller 方法约 1ms 就返回 SseEmitter 对象,**真正的 token 推送发生在方法返回之后**。`@Around` 切面在方法返回时退出,根本拿不到流式过程中的 TokenUsage:

```
T=0      Controller 返回 SseEmitter           ← @Around 在这里"结束"
T=0.001  方法返回,AOP 切面退出
T=0~5    LLM 流式推送 token(几秒钟)
T=5      onComplete 触发,TokenUsage 此时才有  ← AOP 已经看不到
```

LangChain4j 1.11 提供了 `ChatModelListener` SPI,是框架原生的"切面",`onResponse(ChatResponse)` 里能拿到完整 TokenUsage,流式和非流式都覆盖。这才是 token 计数的正确抽象层。

但 @Aspect 并非完全无用——它仍然是 timing、traceId 注入、错误兜底日志等横切关注点的天然载体。问题不是"二选一",而是"职责怎么分"。

## 候选方案

### 方案 1:全部走 @Aspect

**优点**:
- Spring 生态熟悉,调试体验好,断点栈清晰
- 与 ROADMAP Week 3 原任务描述一致

**缺点**:
- SSE 流式场景下,`@Around` 在 SseEmitter 返回时即退出,拿不到 TokenUsage(根本性缺陷)
- 需要手动包装 SseEmitter 的 `onCompletion` 回调来追加切面逻辑,代码侵入性强
- AOP 在跨线程场景下 MDC 上下文会丢,需要额外的 snapshot 机制(LEARNING-NOTES 笔记 4 已记录)
- LangChain4j 后续如果引入同步 `chat()`、批量调用、Embedding 模型,需要多套切点适配

### 方案 2:全部走 ChatModelListener

**优点**:
- LangChain4j 框架原生设计的扩展点,流式/非流式统一覆盖
- `onResponse` 拿完整 ChatResponse 含 TokenUsage,无需手工累加
- `onError(ChatModelErrorContext)` 是失败场景统一入口,与 Day 2 已落库的 `BillingStatus` 枚举完美对应
- `ChatModelRequestContext.attributes()` 提供跨阶段上下文传递,优于 MDC(MDC 在流式跨线程会丢)
- spring-boot-starter 自动把 `@Component` 类型为 `ChatModelListener` 的 Bean 注入到所有 ChatModel,零配置

**缺点**:
- 框架特定,职责扩张到"请求 timing / 业务日志 / 错误兜底"会形成"上帝 Listener"
- timing 不准:Listener 的 `onRequest` 到 `onResponse` 时长是 LLM 调用本身的时长,**不是业务请求的总耗时**(中间还有 Spring MVC 调度、SseEmitter 准备等)
- traceId 注入这种"请求入口"职责本来就该走 Filter,不该塞进 Listener

### 方案 3:双层职责分明(本 ADR 采纳)

将横切关注点按"和 LLM 计费数据相关 / 不相关"切两半:

- **和 LLM 计费数据相关** → ChatModelListener(token 计数、按 provider 算费用、写 BillingLog)
- **和 LLM 计费数据无关** → @Aspect / Filter(timing、traceId、业务级错误日志)

**优点**:
- 每个工具用在自己最擅长的场景,不强行扩展
- 边界清晰,代码可读性高,各自独立演进
- 未来若引入 Micrometer/OpenTelemetry,指标采集可以再独立成第三层,不影响计费逻辑

**缺点**:
- 需要维护两套切入机制(@Aspect + Listener),概念上比单一方案多
- requestId 等上下文要在两套机制间传递,需要约定传递载体(本 ADR 决定用 `ChatModelRequestContext.attributes()` + MDC 双轨)

## 最终决策

**方案 3 — 双层职责分明架构**。

### 分工表(权威版)

| 关注点 | 使用机制 | 触发时机 |
|--------|----------|----------|
| traceId / userId 注入 | Filter / Interceptor | 请求入口最早时刻 |
| 请求开始/结束 timing | `@Aspect @Around`(Controller 层) | 业务方法进入/退出 |
| Controller 层异常兜底日志 | `@Aspect @AfterThrowing` | 业务方法抛出未处理异常时 |
| **Prompt / Completion Token 计数** | **`ChatModelListener.onResponse`** | LLM 流式/同步调用完成时 |
| 按 provider 单价算费用 | `ChatModelListener.onResponse` | 同上 |
| 写 BillingLog(SUCCESS) | `ChatModelListener.onResponse` | 同上 |
| 写 BillingLog(FAILED / RATE_LIMITED / ERROR_RESPONSE) | `ChatModelListener.onError` | LLM 调用抛错时,按异常类型分类 |
| 流式 token 推送给前端 | `StreamingChatResponseHandler.onPartialResponse` | 每个 token 流出时(框架接口,与本 ADR 无关) |

### 关键理由

1. **匹配 SSE 流式架构(ADR-001)**:Listener 是唯一能在流式完成后拿到完整 TokenUsage 的扩展点。这是技术约束,不是审美选择。
2. **匹配 LangChain4j 框架习惯(ADR-004)**:Listener 是框架原生 SPI,不需要发明轮子。spring-boot-starter 自动装配,零配置心智负担。
3. **匹配未来路径**:M9 引入异步落库时,只需把 Listener 内的 `repository.save()` 换成 `kafkaTemplate.send()`,Listener 接口本身零改动。
4. **匹配学习项目目标**:M1 Week 3 是"中间件首次落地"周,刻意走 @Aspect + Filter + Listener 三套机制并存,把 Spring 横切关注点的肌肉记忆补回来(ADR-004 已知代价的补救计划)。

## 已知代价

1. **框架耦合**:ChatModelListener 是 LangChain4j 专属 SPI。若 ADR-004 被推翻(整体切到 Spring AI 等其他框架),Listener 全部需要重写。但 LangChain4j 内换 Provider(M2 任务)不受影响——Listener 是模型抽象级别的,不是 Provider 级别的。

2. **跨阶段上下文传递的双轨复杂度**:
   - MDC 用于同步链路 + 日志输出(Filter 注入,@Aspect 读取)
   - `ChatModelRequestContext.attributes()` 用于跨线程的 Listener 内部(onRequest 塞,onResponse / onError 取)
   - 两套机制并存,理解曲线略陡。缓解方式:LEARNING-NOTES 笔记 4 已记录"流式回调跨线程"的根因。

3. **观测性 vs 计费的混淆边界**:Listener 同时承载 token 计数(计费职责)和监控指标(观测职责)。后续若引入 Micrometer / OpenTelemetry,需重新评估指标采集层是否独立成第三个 Listener,或复用本 ADR 的 BillingListener。**M1 阶段不分,M9 监控接入时再切**。

4. **timing 不准的隐性陷阱**:有人会误以为 `onRequest` 到 `onResponse` 的时长是"业务请求耗时",其实只是 LLM 调用本身的耗时。Controller 层的请求总耗时仍需 @Aspect 测量。**这一点须在代码注释和团队约定中明示**。

## 重新审视的触发条件

- ADR-004 决策被推翻(LangChain4j 整体被替换)
- LangChain4j 2.x 版本 ChatModelListener 接口大幅变更
- M9 引入异步落库(本 ADR 不变,只是 Listener 内部实现切换)
- 性能压测发现 Listener 同步落库成为 SSE 长连接的瓶颈(目前预估不会,M5 压测验证)
- 引入 Spring AI 与 LangChain4j 并存(需要在两个框架之上做一层计费抽象,本 ADR 退化为"实现细节")

## 实施进度

- ✅ **Day 3**:`BillingListener implements ChatModelListener` 骨架完成,三个回调日志可观察
- ✅ **Day 3**:验证 LangChain4j spring-boot-starter 的自动装配机制(`@Component` Bean 被自动注入到 streaming-chat-model)
- ✅ **Day 3**:观察到流式回调跨线程现象(`onRequest=main`、`onError=ForkJoinPool`),确认必须用 `attributes` 而非 MDC 传递跨阶段上下文
- ✅ **Day 4 Step 2**:`requestId` 通过 `ChatModelRequestContext.attributes()` 注入与读取,跨线程稳定工作
- ✅ **Day 4 Step 4**:`BillingStatusClassifier` 完成 LangChain4j 异常体系到四值 BillingStatus 的映射(`instanceof` 判断,8 个单测覆盖)
- ✅ **Day 4 Step 4**:`onError` → BillingLog 按异常类型分类落库,三类样本验证(`UnresolvedModelServerException` → FAILED / `AuthenticationException` → ERROR_RESPONSE / `RateLimitException` → RATE_LIMITED 路径在 Classifier 单测覆盖)
- ✅ **Day 4 Step 5a**:`RequestIdFilter` 在 HTTP 入口装填 MDC + 响应头 `X-Request-Id`,MockMvc 双测试覆盖
- ✅ **Day 4 Step 5b**:`RequestLoggingAspect` `@Around` 测 controller setup 耗时,`@AfterThrowing` 兜底异常日志,`within(@RestController *)` pointcut 覆盖未来所有 controller
- ✅ **Day 4 Step 3**:`onResponse` → BillingLog(SUCCESS)落库(原计划挂起,Day 4 末因笔记 8 描述的反证链解锁顺手完成)
## 实际效果(事后补充)

**M1 Day 4 末回填**:

Listener + @Aspect + Filter 三层架构在 Day 4 末通过 `spring-boot:run` + curl 形成完整端到端验证。关键观察:

1. **跨阶段 `attributes` 传递稳定**:三次实测(FAILED / ERROR_RESPONSE / SUCCESS 三种路径)中,requestId 在 onRequest(Tomcat handler 线程)和 onResponse / onError(ForkJoinPool 线程)之间无丢失,无需 MDC snapshot 等额外机制。

2. **四类 BillingStatus 够用**:Day 4 实测中拿到 `UnresolvedModelServerException` → FAILED、`AuthenticationException` → ERROR_RESPONSE、SUCCESS 三个真实样本。原始的 `RATE_LIMITED` 暂未在实测中触发(需要刻意构造 429 场景),但 Classifier 已覆盖。Day 4 没发现需要新增的状态类别。

3. **timing 准确性的"诚实标注"是关键**:`controllerSetupMs=24ms` vs LLM 真实耗时 `latencyMs=1067ms`(BillingListener 测的),44 倍差距。如果 controllerSetupMs 字段叫 `latencyMs`,半年后看日志的人(包括我自己)100% 会误以为这是 LLM 调用耗时。**ADR-006 已知代价 #4 "timing 不准的隐性陷阱"被这次实测验证为真实风险,字段名诚实标注是有效的缓解措施**。

4. **listener 内同步 save 在低并发场景无问题**:Day 4 实测三次 curl 都在 30ms 内完成 BillingLog 落库,无阻塞 SSE 长连接的现象。M5 压测时需重新验证 50 并发下的表现。

**M5 压测时再回填**:Listener 同步落库在 50 并发 SSE 长连接下的实际延迟与连接池占用情况。