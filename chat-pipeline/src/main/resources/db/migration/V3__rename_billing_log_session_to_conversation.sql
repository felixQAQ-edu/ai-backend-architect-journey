-- ============================================================================
-- V3__rename_billing_log_session_to_conversation.sql
-- M1 Week 3 Day 5: BillingLog.session_id → conversation_id
--
-- 背景:CONTEXT.md v0.1.1 已锁定术语为 conversationId(不用 sessionId 避免与
-- HTTP Session、Hibernate Session 撞名)。Day 2 写 V1 时 CONTEXT.md 还未建立,
-- 留下 session_id 字段。Day 5 趁 Week 3 收尾顺手对齐——延迟越久,引用越多,
-- 重命名成本越高。
--
-- 风险评估:M1 Week 3 期间 session_id 列从未被业务代码填充(BillingListener.java
-- 不调用 setSessionId,Day 4 实测落库的 BillingLog 记录该列全为 NULL),所以无需
-- backfill 数据,直接 RENAME COLUMN。
--
-- 兼容性:
--   - H2: 2.x+ 支持 ALTER TABLE ... RENAME COLUMN
--   - PostgreSQL: 是 SQL 标准语法
-- ============================================================================

ALTER TABLE billing_log RENAME COLUMN session_id TO conversation_id;
