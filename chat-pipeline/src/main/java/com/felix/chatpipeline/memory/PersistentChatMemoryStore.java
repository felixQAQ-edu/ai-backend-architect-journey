package com.felix.chatpipeline.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * ChatMemoryStore 的 JPA 持久化实现。
 *
 * <p>设计参见 ADR-007。沿用 delete-then-insert 全量替换策略,与 LangChain4j 官方
 * "updateMessages 每次传完整列表"的语义零歧义对齐(详见 ADR-007 候选方案 vs 最终决策)。
 *
 * <p>三个方法的实现:
 * <ul>
 *   <li>{@link #getMessages}:按 message_index 升序查询;空结果返回 emptyList,
 *       不调用 LangChain4j {@code ChatMessageDeserializer}
 *       (避开 Issue #3295 的空字符串反序列化陷阱)</li>
 *   <li>{@link #updateMessages}:{@code @Transactional} + delete-then-insert
 *       全量替换。两个 DB 写(DELETE + N 个 INSERT)在同一事务内,
 *       LangChain4j 调用方拿到方法返回时数据已 commit</li>
 *   <li>{@link #deleteMessages}:单条 SQL 清空该 conversation 所有行</li>
 * </ul>
 *
 * <p>跨阶段上下文:LangChain4j 每次 {@code chat()} 调用过程中会触发 updateMessages
 * 两次(UserMessage 加入时一次,AiMessage 加入时再一次),每次传入的是该 memoryId 对应的
 * 完整 List。全量替换比 append 实现更简单,evict 边界场景无 bug 风险。
 */
@Component
public class PersistentChatMemoryStore implements ChatMemoryStore {

    private final ConversationMessageRepository repository;
    private final ChatMessageConverter converter;

    public PersistentChatMemoryStore(ConversationMessageRepository repository,
                                     ChatMessageConverter converter) {
        this.repository = repository;
        this.converter = converter;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        List<ConversationMessage> rows = repository
                .findByConversationIdOrderByMessageIndexAsc(memoryId.toString());
        if (rows.isEmpty()) {
            // 显式空 list 短路,避免下游误用 ChatMessageDeserializer 反序列化空字符串
            // (Issue #3295)
            return Collections.emptyList();
        }
        return rows.stream()
                .map(converter::toChatMessage)
                .toList();
    }

    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String conversationId = memoryId.toString();
        // delete-then-insert 全量替换(ADR-007 实现策略)
        repository.deleteByConversationId(conversationId);
        for (int i = 0; i < messages.size(); i++) {
            ConversationMessage entity = converter.toEntity(conversationId, i, messages.get(i));
            repository.save(entity);
        }
    }

    @Override
    @Transactional
    public void deleteMessages(Object memoryId) {
        repository.deleteByConversationId(memoryId.toString());
    }
}
