package com.felix.chatpipeline.memory;

/**
 * ChatMemoryStore 持久化层的消息角色枚举。
 *
 * <p>沿用 LangChain4j {@code dev.langchain4j.data.message.ChatMessageType} 命名,
 * 不直接复用框架枚举的理由:DB 列定义层不应耦合 LangChain4j 类型
 *(同样的解耦理由参见 {@code BillingStatus} 不直接复用 LangChain4j 异常类)。
 *
 * <p>新增枚举值的触发条件:LangChain4j 在 ChatMessageType 中新增类型
 *(例如 ImageMessage 升为顶层消息类型)。届时需要:
 * <ol>
 *   <li>在此 enum 中新增对应常量</li>
 *   <li>ChatMessage ↔ Entity 双向转换器(Step 3)加 case</li>
 *   <li>重审 ADR-007:VARCHAR(32) 列宽是否仍足够</li>
 * </ol>
 *
 * <p>当前最长常量 {@link #TOOL_EXECUTION_RESULT} 为 21 字符,DB 列 VARCHAR(32) 留有余量。
 */
public enum MessageRole {
    USER,
    AI,
    SYSTEM,
    TOOL_EXECUTION_RESULT
}
