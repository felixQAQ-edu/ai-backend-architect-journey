package com.felix.chatpipeline.dto;

/**
 * 流式对话请求体。
 * <p>
 * sessionId 用于 ChatMemory 隔离不同会话；客户端自己生成（UUID）即可。
 */
public record ChatRequest(
        String sessionId,
        String message
) {}
