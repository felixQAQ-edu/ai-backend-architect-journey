package com.felix.chatpipeline.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * conversation_message 表的 Spring Data 仓储。
 *
 * <p>设计参见 ADR-007。两个查询方法服务于 Step 4 的 ChatMemoryStore 实现:
 * <ul>
 *   <li>{@link #findByConversationIdOrderByMessageIndexAsc}:支撑 {@code getMessages},
 *       消息按入库顺序读出</li>
 *   <li>{@link #deleteByConversationId}:支撑 {@code updateMessages} 走的
 *       delete-then-insert 全量替换,用显式 JPQL 而非派生查询,
 *       避免 Spring Data 派生 deleteByXxx 的隐式 N+1(先 SELECT 拉所有 entity
 *       再逐行 DELETE)</li>
 * </ul>
 */
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findByConversationIdOrderByMessageIndexAsc(String conversationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ConversationMessage cm where cm.conversationId = :conversationId")
    void deleteByConversationId(@Param("conversationId") String conversationId);
}
