package com.felix.chatpipeline.config;

import com.felix.chatpipeline.memory.PersistentChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多轮对话记忆配置。
 * <p>
 * 工作机制:
 * <ol>
 *   <li>{@code KnowledgeAssistant.chat(@MemoryId String conversationId, ...)} 接收 conversationId</li>
 *   <li>LangChain4j 用 conversationId 调用 {@code ChatMemoryProvider.get(conversationId)} 拿对应 ChatMemory</li>
 *   <li>每次对话前后,LangChain4j 自动调用 {@code ChatMemoryStore.updateMessages()} 持久化历史消息</li>
 *   <li>应用重启或跨进程后,根据 conversationId 重新调用 {@code ChatMemoryStore.getMessages()} 恢复上下文</li>
 * </ol>
 * <p>
 * 持久化层切换历史:
 * <ul>
 *   <li>初版(M1 Week 3 之前):未配 chatMemoryStore,LangChain4j 默认 InMemoryChatMemoryStore,
 *       应用重启即丢失(适合开发起步,不适合真实多轮记忆场景)</li>
 *   <li>当前(M1 Week 3 Day 5):接入 {@link PersistentChatMemoryStore},基于 JPA 持久化到 H2/PostgreSQL,
 *       设计与实现细节见 ADR-007</li>
 * </ul>
 * <p>
 * maxMessages = 20 的取数依据(ADR-007):tool call 一来一回占 2 条消息,
 * 20 条提供约 5-7 轮含 tool 的对话余量,平衡上下文长度与 LLM 计费成本。
 */
@Configuration
public class ChatMemoryConfig {

    /** 每个 conversation 保留最近 N 条消息,超出滑窗 evict 最早的 */
    private static final int MAX_MESSAGES_PER_CONVERSATION = 20;

    @Bean
    public ChatMemoryProvider chatMemoryProvider(PersistentChatMemoryStore store) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(MAX_MESSAGES_PER_CONVERSATION)
                .chatMemoryStore(store)
                .build();
    }
}
