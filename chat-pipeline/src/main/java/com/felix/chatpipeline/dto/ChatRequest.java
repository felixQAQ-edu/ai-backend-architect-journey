package com.felix.chatpipeline.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 流式对话请求体。
 * <p>
 * conversationId 用于 ChatMemory 隔离不同会话;客户端自己生成(UUID)即可。
 * <p>
 * 命名约定来自 CONTEXT.md v0.1.1:不用 sessionId(与 HTTP Session、Hibernate Session
 * 撞名风险高),API 字段统一用 conversationId。
 * <p>
 * {@code @NotBlank} 强校验防御 ADR-007 已知代价 #5:如果 conversationId 为空,
 * LangChain4j AiServices 会回退到默认 memoryId "default",**所有用户共用同一份记忆**——
 * 这是严重 bug。Step 5 通过 controller 层的 {@code @Valid} 提前拦截,空就返回 400。
 */
public record ChatRequest(
        @NotBlank(message = "conversationId must not be blank")
        String conversationId,

        @NotBlank(message = "message must not be blank")
        String message
) {}
