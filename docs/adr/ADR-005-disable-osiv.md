# ADR-005 · 关闭 Open Session In View(OSIV)

- **日期**:2026-05-11
- **状态**:已采纳
- **决策者**:Felix

## 背景

M1 Week 3 Day 2 完成 BillingLog 持久化层后,应用启动日志稳定出现这条 warning:

```
spring.jpa.open-in-view is enabled by default. Therefore, database queries
may be performed during view rendering. Explicitly configure
spring.jpa.open-in-view to disable this warning
```

**OSIV(Open Session In View)是 Spring Boot 的默认行为**:Hibernate Session 从 HTTP 请求进入 Controller 到响应完全渲染期间持续开启。设计初衷是让 view 层(模板、JSON 序列化)可以触发懒加载而不抛 `LazyInitializationException`。代价是 **Session 持有期间会占住 DataSource 的一个连接**。

约束条件:

1. **ADR-001 选定 SSE + Spring MVC**:SSE 是长连接,LLM 流式生成 token 的过程可能持续 5–30 秒,响应"渲染期"远长于普通 REST
2. **M5 压测目标 50 并发**:ROADMAP 进度表里明确目标
3. **HikariCP 默认 `maximum-pool-size = 10`**:Spring Boot 默认值,远低于 50

三条凑一起,OSIV 开启 = "一个 SSE 连接独占一个 DB 连接 5–30 秒"。10 并发就把连接池吃干,远到不了 M5 目标。

## 候选方案

### 方案 1:关闭 OSIV(`spring.jpa.open-in-view: false`)

**优点**:
- 彻底解决连接池被长连接占住的问题:Session 只在 `@Transactional` 方法内开启
- 强制开发者用 `@Transactional` 显式标注事务边界,代码意图清晰
- Vlad Mihalcea 等 Hibernate 专家长期推荐的工业最佳实践
- 与 SSE 长连接 + 50 并发目标完全匹配

**缺点**:
- 在事务外触发懒加载会抛 `LazyInitializationException`
- service 层方法必须显式标注 `@Transactional`,容易漏标
- 调试 `LazyInitializationException` 比 OSIV 隐式开 Session 略麻烦

### 方案 2:保持 OSIV 开启,约定 SSE 流期间不访问 DB

**优点**:不改默认配置

**缺点**:
- 通过约定保证而非强制,约定一定会被破坏
- **即使代码层面真的不访问 DB,Hibernate Session 持有期间仍然占住一个 HikariCP 连接** —— 连接池问题根本没解决
- 排除

### 方案 3:扩大 HikariCP 连接池(`maximum-pool-size: 100`)

**优点**:暴力,无需改代码

**缺点**:
- 连接池上限受 DB 自身限制,100 个连接在生产环境很激进
- band-aid,根本问题(Session 持有时间过长)未解决
- 浪费连接资源 —— 真正需要 DB 的时刻可能只几十毫秒,却独占 30 秒
- 排除

## 最终决策

**方案 1 —— 在 `application.yml` 显式关闭 OSIV:**

```yaml
spring:
  jpa:
    open-in-view: false
```

### 关键理由

1. **匹配 SSE + 50 并发目标**:ADR-001 + M5 压测目标决定了"长连接 + 中等并发"是常态。OSIV 默认行为是为传统短请求 + JSP 渲染场景设计,与本项目场景不匹配
2. **强制事务边界显式化**:M1 之后 BillingLog / ChatMemory 等持久化逻辑都会进 service 层,`@Transactional` 显式标注让事务范围一目了然,对未来调试 / 性能分析友好
3. **现在改成本极低**:M1 Day 2 只有 BillingLog 一个 Entity,无关联映射,无懒加载场景。等业务铺开后再改成本会大很多
4. **顺手扫掉启动 warning**:启动日志干净,后续真出问题时 warning 才有信号价值

## 已知代价

1. **`LazyInitializationException` 风险**:未来引入 `@ManyToOne(fetch = LAZY)` 等映射时,view 层触发懒加载会抛异常。缓解方式:service 层用 `@Transactional` 或 fetch join 提前加载好需要的数据
2. **service 层方法必须显式 `@Transactional`**:这其实是好事(意图显式化),但需要养成肌肉记忆
3. **JSON 序列化层懒加载需要小心**:Jackson 序列化 Entity 时若访问到未初始化的关联,会抛异常。缓解方式:用 DTO + MapStruct 而不是直接序列化 Entity(本来就是好实践)

## 重新审视的触发条件

- 实测发现关闭 OSIV 后,因为漏写 `@Transactional` 导致的 bug 频率高得离谱(预期不会,但记一下)
- 未来引入 Spring AI / 全响应式栈,事务模型可能整个换掉,本决策需重审
- ADR-001 的 SSE 选型被推翻(例如切到 WebFlux),OSIV 不再适用

## 实施步骤

1. `src/main/resources/application.yml` 的 `spring.jpa` 节点下加 `open-in-view: false`
2. 重启应用,确认启动日志中 OSIV warning 消失
3. 跑现有测试套件(`BillingLogSchemaTest` + `BillingLogRepositoryTest`),确认全绿

## 实际效果(事后补充)

_M1 结束时回填:关闭 OSIV 后是否踩过 `LazyInitializationException`?`@Transactional` 的使用频率如何?_

_M5 压测时回填:50 并发场景下连接池占用率如何?_
