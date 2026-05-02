# ADR-002 · Spring Boot 版本选型（3.5 vs 4.0）

- 日期：2026-04-30
- 状态：已采纳

## 背景

M1 启动时，Spring Initializr 默认推荐 Spring Boot 4.0.x（4.0.6 stable，2026-03 发布）。
但本项目是 AI 后端学习路线，核心依赖 LLM 框架生态（LangChain4j 与 Spring AI），
版本选型必须与上层框架兼容性挂钩。

约束条件：
1. 项目第一个里程碑（M1）就要接入 LLM 框架（ADR-003 候选议题）
2. ROADMAP 已规划 ADR-003 在 LangChain4j / Spring AI / 直连 SDK 三者间做选型
3. 学习项目优先稳定性，避免被上游 milestone 版本的 breaking change 拖累

## 候选方案

### 方案 1：Spring Boot 3.5.14（3.5 系列最新 patch）

**优点：**
- LangChain4j 1.11.x 官方支持（文档明确：「requires Java 17 and Spring Boot 3.5」）
- Spring AI 1.1.x 稳定版基于 Spring Boot 3.x
- 是 ADR-003 选型时**所有候选项都可用**的版本
- 生态成熟，stack overflow / GitHub issues 数量巨大，遇到问题有兜底
- JDK 21 + 虚拟线程在 3.5 系列已是一等公民，与 ADR-001 升级路径完全兼容

**缺点：**
- 不是最新大版本，错过 Boot 4.0 / Spring Framework 7.0 的部分新特性
  （Jackson 3、Jakarta EE 11、内置 API Versioning、内置 Authorization Server 等）
- 长期来看会面临一次"升级到 Boot 4"的迁移成本

### 方案 2：Spring Boot 4.0.6（最新稳定大版本）

**优点：**
- Spring Framework 7.0 / Jackson 3 / Jakarta EE 11 / JSpecify 全栈最新
- 内置 OpenTelemetry starter、API Versioning，未来 M9-M10 高可用阶段直接用得上
- 简历上写「Spring Boot 4」更亮眼

**缺点：**
- **LangChain4j 当前不兼容**（GitHub issue #4268 已确认 autoconfig 报错，
  内部类 import 路径在 Boot 4 被搬走）。官方表示「planned for a future release」，
  但没有明确时间表
- Spring AI 2.0 仍在 milestone（M3，2026-03），不是稳定版
- 选 Boot 4 等于把 ADR-003 的候选范围**预先压缩到「Spring AI 2.0-milestone」或「直连 SDK」**，
  用版本选型偷偷替 ADR-003 做了决定，违反「一次决策一个变量」的原则
- 学习项目第一周引入 milestone 版本依赖，调试时容易踩 framework bug，干扰主线

### 方案 3：等 LangChain4j 支持 Boot 4 后再启动

**优点：** 同时拿到 Boot 4 + LangChain4j

**缺点：** 没有时间表，可能一个月也可能半年；为了"等"而停滞与本项目「立刻动手」的节奏冲突。直接排除。

## 最终决策

**方案 1 — Spring Boot 3.5.14。**

关键理由：
- **保留 ADR-003 的所有候选**：选 3.5 后，LangChain4j / Spring AI / 直连 SDK 三个候选都可用，
  ADR-003 才是真正基于框架特性做选型，而不是被版本选型偷偷限定
- **匹配阶段目标**：M1-M2 的核心是「管道优先」，需要稳定生态做底，而不是追新
- **学习曲线友好**：3.5 系列文档、教程、stackoverflow 答案数量级远超 4.0
- **ADR-001 的升级路径不受影响**：JDK 21 虚拟线程在 3.5 上完全可用

## 已知代价

1. 错过 Boot 4 / Spring Framework 7 / Jackson 3 等新特性。学习价值有损失，但 M1-M2 用不到
2. 未来某天需要做一次 Boot 3.5 → 4.x 的迁移。预计触发条件：
   - LangChain4j 正式支持 Boot 4（届时本 ADR 应被新 ADR 取代）
   - 或 Spring AI 2.0 GA 且本项目决定切换到 Spring AI
   - 或 Boot 3.5 进入 OSS 维护尾声（按 Spring 支持周期，约 2027 年）
3. 简历上需要解释「为什么不用最新版」——但这个解释本身（基于生态兼容性的工程权衡）
   就是简历亮点，不是减分项

## 实际效果（事后补充）

_M2 结束时回填：3.5.14 是否平稳运行？是否遇到需要 Boot 4 特性才能解决的痛点？_
