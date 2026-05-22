package com.felix.chatpipeline.memory;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * ChatMemoryStore 持久化层的消息实体,对应 conversation_message 表。
 *
 * <p>设计参见 ADR-007。核心要点:
 * <ul>
 *   <li>平字段 + 非平 JSON:role/text 等拆字段(SQL 查询友好),
 *       aiMessagePayloadJson 由 LangChain4j {@code ChatMessageSerializer} 生成,
 *       仅对 {@link MessageRole#AI} 行填,让框架兜底 text/thinking/
 *       toolExecutionRequests/attributes 等字段集随 1.x 版本演进</li>
 *   <li>(conversationId, messageIndex) UNIQUE:Day 5 走 delete-then-insert 时
 *       不直接发挥作用,但保留未来 upsert by index 演进的可能</li>
 *   <li>toolCallId / toolName 仅 TOOL_EXECUTION_RESULT role 填,其他 role 永远为 null</li>
 * </ul>
 *
 * <p>风格对齐 {@code BillingLog}:无 Lombok、手写 getter/setter、equals/hashCode 基于业务键。
 */
@Entity
@Table(name = "conversation_message",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_conv_msg_idx",
               columnNames = {"conversation_id", "message_index"}))
public class ConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String conversationId;

    @Column(nullable = false)
    private int messageIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MessageRole role;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(columnDefinition = "TEXT")
    private String aiMessagePayloadJson;

    @Column(length = 64)
    private String toolCallId;

    @Column(length = 128)
    private String toolName;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public ConversationMessage() {
        // JPA requires no-arg constructor
    }

    // ---------- getters & setters ----------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public int getMessageIndex() { return messageIndex; }
    public void setMessageIndex(int messageIndex) { this.messageIndex = messageIndex; }

    public MessageRole getRole() { return role; }
    public void setRole(MessageRole role) { this.role = role; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getAiMessagePayloadJson() { return aiMessagePayloadJson; }
    public void setAiMessagePayloadJson(String aiMessagePayloadJson) {
        this.aiMessagePayloadJson = aiMessagePayloadJson;
    }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public Instant getCreatedAt() { return createdAt; }
    // 同 BillingLog:没有 setCreatedAt,@CreationTimestamp 自己填,业务代码不许动

    // ---------- equals & hashCode (业务键 = conversationId + messageIndex) ----------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConversationMessage that)) return false;
        return messageIndex == that.messageIndex
                && Objects.equals(conversationId, that.conversationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationId, messageIndex);
    }

    @Override
    public String toString() {
        return "ConversationMessage{id=" + id +
                ", convId='" + conversationId + '\'' +
                ", idx=" + messageIndex +
                ", role=" + role +
                ", textLen=" + (text == null ? 0 : text.length()) +
                ", hasAiPayload=" + (aiMessagePayloadJson != null) +
                '}';
    }
}
