# ADR-008 · 删除自定义 LangChain4jHttpClientConfig,回归 starter 默认装配

- **日期**:2026-05-24
- **状态**:已采纳
- **决策者**:Felix

## 背景

本 ADR 关闭一个跨 Week 3 / Week 4 的未结循环 —— **笔记 7 / 笔记 8 主题**。

### 循环来历

M1 Week 3 Day 3(2026-05-15)启动 BillingListener 真实流式调用时,所有 `BillingListenerSmokeTest` 抛 `UnresolvedAddressException`,35ms 失败,不出网络。按笔记 5 排查清单逐层排除(业务代码 / 框架配置 / 框架版本 / JVM 参数 / 构建工具 / JDK 版本 / OS 网络栈 / 物理网络)后,**笔记 7 锁定"JDK 21 macOS HttpClient sendAsync 暗坑"为根因**。

防御性引入 `LangChain4jHttpClientConfig`:自定义 `HttpClientBuilder` bean,强制 HTTP/1.1 + `ProxySelector.of(null)` + 30s connect timeout,借助 starter 的 `@ConditionalOnMissingBean(names="openAiStreamingChatModelHttpClientBuilder")` 跳过默认装配。

### 笔记 8 推翻笔记 7 根因假设

Day 4 末三次 curl 形成的反证链证明:
- 同一份代码,`spring-boot:run` 路径 → curl 成功(401 / 200 OK)
- IDEA Test Runner 路径 → `UnresolvedAddressException` 35ms 失败

**唯一变量是运行方式**(IDEA Test Runner 的 agent 注入)而非 JDK 本身。笔记 7 假设的"JDK 暗坑"是诊断纪律失误的产物 —— 排查 8 步全过仍复现时,把"未尝试的变量"误当成"已排除完",从而锁错根因。

笔记 8 形成时,Week 3 收尾时间紧张,`LangChain4jHttpClientConfig` 选择"保留观察",真实必要性留 Week 4+ 暖机任务反证。

### Week 4 Day 0 暖机 2 反证(2026-05-24)

按"一次决策一个变量 + IDE 全文搜建立客观清单"(笔记 10 P0)纪律推进:

| 实验组 | 配置 | 运行方式 | LLM | 结果 |
|--------|------|----------|-----|------|
| **A baseline** | 保留 | `spring-boot:run` | OpenAI gpt-4o-mini | SUCCESS · BillingLog id=9 · latencyMs=2302 · HTTP/1.1 · `ForkJoinPool.commonPool-worker-1` |
| **B 实验组** | `@Bean` 注释 | `spring-boot:run` | OpenAI gpt-4o-mini | SUCCESS · BillingLog id=10 · latencyMs=1557 · HTTP/2 · `LangChain4j-OpenAI-1` |

**反证核心结论**:`spring-boot:run` 路径下,无论 `LangChain4jHttpClientConfig` 在不在,`UnresolvedAddressException` 都不复现 —— **该配置在生产运行环境下不必要**。

实物证据有两条:

- **线程池命名变化**:`ForkJoinPool.commonPool-worker-1`(JdkHttpClient 默认 sendAsync executor)→ `LangChain4j-OpenAI-1`(starter 默认装配后由 LangChain4j 自身维护的线程池),证明 `@ConditionalOnMissingBean` 机制真的触发了 fallback
- **HTTP 协议层变化**:Baseline 响应头有 `transfer-encoding: chunked`(HTTP/1.1 特征)且无 `:status` 伪头;Experiment 反过来(HTTP/2 特征),证明 fallback 后默认装配走 HTTP/2

## 候选方案

### 方案 1:保留配置(防御性 talisman)

**优点**:保留笔记 7 时引入的"防御性配置",未来若类似 `UnresolvedAddressException` 在某种场景再现,可能误以为"是因为有这个配置才没复现"

**缺点**:
- 反证已证明 `spring-boot:run` 路径下不必要
- 留下来变成"根因未明的防御性代码" = 技术债
- 未来工程师(包括半年后的 Felix 自己)看到这段会怀疑"是不是有必要 / 删了会出事",形成不必要的认知负担
- 与项目工程纪律"反证完了就清理"冲突

### 方案 2:删除配置 + 让 starter 默认装配生效(本 ADR 采纳)

**优点**:
- 反证主线证据充分:`spring-boot:run` 路径 SUCCESS + 三层中间件全链路工作 + BillingLog 落库
- 体现项目工程纪律 —— 决策可被推翻,推翻要有证据,推翻后彻底清理
- 默认装配走 HTTP/2,性能可能更优(latencyMs 减少 32%,样本太小不下定论但方向利好)
- 代码库瘦身,降低认知负担

**缺点**:
- 失去 HTTP/1.1 强制配置 —— 如果未来某些 LLM provider / 网络栈对 HTTP/2 不友好,需要重新配置(预估概率低,HTTP/2 是 2026 年绝大多数 LLM provider 的主流)
- 失去 `ProxySelector.of(null)` 显式无代理 —— 如果未来某开发者机器装了 corporate proxy / macOS System Proxy 错误影响应用请求,可能踩坑(缓解:JVM args 或 `application.yml` 可重新配置)
- IDEA Test Runner 路径下打真实 LLM 是否会重新触发原始 `UnresolvedAddressException`,这条路径仍是未深究的 unknown(笔记 8 已接受这个代价 —— ROI 低)

### 方案 3:保留配置但加 `@ConditionalOnProperty` 默认 disabled

**优点**:留作"按需启用",未来踩坑时不用重写

**缺点**:过度工程 —— 反证已证明不需要,留 disabled 的代码不如直接删除 + 走 git 历史找回。**排除。**

## 最终决策

**方案 2 —— 删除 `LangChain4jHttpClientConfig.java`,回归 starter 默认装配。**

### 关键理由

1. **反证证据充分**:Week 4 Day 0 暖机 2 A/B 对照 SUCCESS,`UnresolvedAddressException` 0 出现,三层中间件链完整工作
2. **匹配项目工程纪律**:笔记 8 元教训"已经反证完的循环不再投入" + 笔记 10"全局变更前置纪律"的延伸 —— 反证完了不光是"得出结论",还要"清理实施"
3. **降低认知负担**:删除一段"根因未明的防御性配置",未来工程师不会被它误导
4. **保留 git 历史兜底**:即使万一未来需要,`git log --all --full-history -- src/main/java/com/felix/chatpipeline/config/LangChain4jHttpClientConfig.java` 可以找回

## 已知代价

1. **HTTP/2 vs HTTP/1.1 不可控**:starter 默认装配走 HTTP/2,与之前显式 HTTP/1.1 不同。如果未来某个 LLM provider 不支持 HTTP/2(预估极低概率),需要重新配置。缓解方式:遇到时再加新的 ADR

2. **显式无代理保护消失**:之前 `ProxySelector.of(null)` 强制忽略系统代理,删除后由 JDK / starter 默认行为决定。在 macOS / 含 corporate proxy 的开发环境可能踩坑。缓解方式:出问题时用 JVM args `-Dhttps.proxyHost=...` 或 `application.yml` 配置

3. **IDEA Test Runner 原始问题路径仍未深究**:笔记 8 已经把"根因未深究"作为已知代价接受,本 ADR 不重新打开这个循环。代价表现为:**Week 4+ 如果再次在 IDEA Test Runner 下打真实 LLM,可能复现 `UnresolvedAddressException`**。缓解方式:用 `https://test.invalid/v1` 占位 URL 在 test profile 防御(`src/test/resources/application.yml` 已经这样配置,见 Week 4 Day 0 暖机 1 验证)

4. **反证样本量小**:Week 4 Day 0 只跑了 1 次 A + 1 次 B,各打 OpenAI gpt-4o-mini 一次成功。M2 切换到其他 Provider(Ollama / DeepSeek)+ M5 压测 50 并发场景仍需复测,如有异常再开新 ADR

## 重新审视的触发条件

- LangChain4j 升级(1.12+ / 2.x)后,starter 默认 HTTP client 行为变化
- 出现新的 HTTP 客户端层 anomaly(超时、连接池、协议错误),需要自定义干预
- M3 接入本地模型(Ollama / vLLM,OpenAI-compatible 端点)时,默认装配出现不兼容
- M9 引入分布式追踪 / 自定义 HTTP header 注入,需要自定义 HttpClient
- M5 压测 50 并发 SSE 长连接场景下默认 HTTP client 表现不佳
- 出现 macOS / corporate proxy 干扰应用请求的真实场景

## 实施步骤

1. **回 main 分支**(`experiment/disable-http-client-config` 上 `@Bean` 注释状态不需要保留 —— 反证证据已沉淀在本 ADR 文本里):
   ```bash
   git checkout main
   ```

2. **删除文件**:
   ```bash
   rm src/main/java/com/felix/chatpipeline/config/LangChain4jHttpClientConfig.java
   ```

3. **IDE 全文搜确认无残留引用**(笔记 10 P0):
   - `Cmd + Shift + F` 搜 `LangChain4jHttpClientConfig`,Scope = Project,无文件后缀过滤
   - 预期 0 命中 *(除了本 ADR 自身、LEARNING-NOTES 笔记 7/8/10 的历史引用、README/CONTEXT 的可能提及)*
   - 如果有意料外的命中,逐一处理后再继续

4. **添加本 ADR 文件**:`docs/adr/ADR-008-remove-http-client-config.md`(本文档完整内容)

5. **更新 README.md**:ADR 列表追加 ADR-008

6. **更新 LEARNING-NOTES**:笔记 7 末尾、笔记 8 末尾各加一行 *"2026-05-24 · 本主题循环已由 ADR-008 关闭,配置删除"*,形成"决策 ↔ 经验"双向闭环

7. **验证测试套件**:
   ```bash
   mvn test
   ```
   预期 31/31 全绿(测试本来就不依赖该配置,test profile 用 `test.invalid` 占位 URL)

8. **验证应用启动 + 真实 LLM curl**:
   ```bash
   mvn spring-boot:run
   ```
   等 `Started ChatPipelineApplication`,另起 terminal:
   ```bash
   curl -N -X POST http://localhost:8080/api/chat/stream \
     -H "Content-Type: application/json" \
     -d '{"conversationId":"w4-d0-adr008-verify-001","message":"你好"}'
   ```
   预期跟 Week 4 Day 0 暖机 2 的 B 组结果一致:
   - SSE chunks 正常流出 + `event:done`
   - spring-boot:run 日志里线程池命名 `LangChain4j-OpenAI-N`
   - BillingLog id=11 落库 status=SUCCESS

9. **commit + push**:
   ```bash
   git add -A
   git commit -m "ADR-008: remove LangChain4jHttpClientConfig (close 笔记 7/8 loop)"
   git push origin main
   ```

10. **清理实验分支**(本地 + 可选远程):
    ```bash
    git branch -D experiment/disable-http-client-config
    # 如果之前 push 过远程,可选删除:git push origin --delete experiment/disable-http-client-config
    ```

## 实际效果(事后补充)

*M2 Provider 切换实验时回填:Ollama / DeepSeek 等其他 Provider 下,starter 默认 HTTP client 是否同样工作?*

*M5 压测时回填:50 并发 SSE 长连接下默认 HTTP client 的稳定性与延迟分布。*

---

## 跟其他文档的交叉引用

- **起源 anomaly**:LEARNING-NOTES 笔记 7(Day 3-4 卡点,锁定 JDK 暗坑假设)
- **推翻笔记 7 假设**:LEARNING-NOTES 笔记 8(诊断纪律失误复盘,Day 4 末三次 curl 反证链)
- **反证证据链**:本 ADR 背景的 A/B 表 + Week 4 Day 0 暖机 2 完整日志(spring-boot:run + curl 控制台输出)
- **工程纪律出处**:LEARNING-NOTES 笔记 8(诊断纪律)+ 笔记 10(全局变更 P0 前置纪律)
- **框架选型基础**:ADR-004(LangChain4j 框架选型)—— 本 ADR 是其 starter 默认装配机制的一次实证
- **配套防御**:`src/test/resources/application.yml` 用 `https://test.invalid/v1` 占位 URL,保证测试不会真打 LLM(顺带规避 IDEA Test Runner 路径的原始问题)
