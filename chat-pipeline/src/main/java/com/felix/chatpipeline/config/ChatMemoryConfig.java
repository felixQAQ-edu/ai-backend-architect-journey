package com.felix.chatpipeline.config;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多轮对话记忆配置。
 * <p>
 * 工作机制：
 * - {@code KnowledgeAssistant.chat(@MemoryId String sessionId, ...)} 接收 sessionId
 * - LangChain4j 用 sessionId 调用 ChatMemoryProvider.get(sessionId) 拿到对应的 ChatMemory
 * - 每次对话前后自动追加 user/assistant 消息到 memory，实现"记得上一轮说什么"
 * <p>
 * 当前是内存版（应用重启会丢）。M1 后续任务可换成持久化版本：
 * 实现 {@link dev.langchain4j.store.memory.chat.ChatMemoryStore} 接口接 SQLite/Postgres，
 * 然后 {@code MessageWindowChatMemory.builder().chatMemoryStore(myStore).build()}
 */
@Configuration
public class ChatMemoryConfig {

    /** 每个会话保留最近 N 条消息，超出滑窗丢弃最早的 */
    private static final int MAX_MESSAGES_PER_SESSION = 20;

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(MAX_MESSAGES_PER_SESSION)
                .build();
    }
}
