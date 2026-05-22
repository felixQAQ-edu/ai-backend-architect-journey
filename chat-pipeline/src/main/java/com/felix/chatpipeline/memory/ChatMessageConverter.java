package com.felix.chatpipeline.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.stereotype.Component;

/**
 * ChatMessage(LangChain4j 类型)↔ ConversationMessage(JPA 实体)双向转换。
 *
 * <p>设计参见 ADR-007:平字段 + 非平 JSON 混合存储。具体到每个 role:
 * <ul>
 *   <li>{@link UserMessage} → {@link MessageRole#USER}:取 singleText 进 text 列;
 *       多模态(图像/音频)显式抛 {@link UnsupportedOperationException}
 *       (M1 暂不支持,M6+ RAG 多模态阶段再扩展)</li>
 *   <li>{@link SystemMessage} → {@link MessageRole#SYSTEM}:取 text 进 text 列</li>
 *   <li>{@link AiMessage} → {@link MessageRole#AI}:text 进 text 列(冗余但 SQL 查询友好),
 *       完整 AiMessage 由 {@link ChatMessageSerializer} JSON 化进 ai_message_payload_json 列
 *       (让框架兜底 text/thinking/toolExecutionRequests/attributes 字段集随版本演进)</li>
 *   <li>{@link ToolExecutionResultMessage} → {@link MessageRole#TOOL_EXECUTION_RESULT}:
 *       text/tool_call_id/tool_name 全部拆字段</li>
 * </ul>
 *
 * <p>反序列化时:USER/SYSTEM/TOOL_EXECUTION_RESULT 直接重建;AI 完全靠
 * {@link ChatMessageDeserializer} 反序列化 ai_message_payload_json
 *(text 列只供 SQL 查询用,不参与重建)。
 */
@Component
public class ChatMessageConverter {

    /**
     * 把 LangChain4j ChatMessage 转成可落库的 Entity。
     *
     * @param conversationId LangChain4j memoryId(业务层 conversationId)
     * @param messageIndex   在该 conversation 中的 0-based 顺序号
     * @param message        LangChain4j ChatMessage
     * @return JPA 实体,未填 id(由 DB 生成)和 createdAt(由 @CreationTimestamp 填)
     * @throws UnsupportedOperationException UserMessage 含非单一 TextContent(M1 暂不支持多模态)
     * @throws IllegalArgumentException 未知的 ChatMessage 子类型(LangChain4j 升级后可能引入)
     */
    public ConversationMessage toEntity(String conversationId, int messageIndex, ChatMessage message) {
        ConversationMessage entity = new ConversationMessage();
        entity.setConversationId(conversationId);
        entity.setMessageIndex(messageIndex);

        if (message instanceof UserMessage um) {
            entity.setRole(MessageRole.USER);
            entity.setText(extractSingleText(um));
        } else if (message instanceof SystemMessage sm) {
            entity.setRole(MessageRole.SYSTEM);
            entity.setText(sm.text());
        } else if (message instanceof AiMessage am) {
            entity.setRole(MessageRole.AI);
            entity.setText(am.text()); // 可能 null(纯 tool call 的 AiMessage)
            entity.setAiMessagePayloadJson(ChatMessageSerializer.messageToJson(am));
        } else if (message instanceof ToolExecutionResultMessage term) {
            entity.setRole(MessageRole.TOOL_EXECUTION_RESULT);
            entity.setText(term.text());
            entity.setToolCallId(term.id());
            entity.setToolName(term.toolName());
        } else {
            throw new IllegalArgumentException(
                    "Unsupported ChatMessage subtype: " + message.getClass().getName()
                            + ". Likely LangChain4j has introduced a new ChatMessage type; "
                            + "update MessageRole enum and this converter.");
        }

        return entity;
    }

    /**
     * 把 Entity 反向重建为 LangChain4j ChatMessage(供 ChatMemoryStore.getMessages 使用)。
     */
    public ChatMessage toChatMessage(ConversationMessage entity) {
        return switch (entity.getRole()) {
            case USER -> UserMessage.from(entity.getText());
            case SYSTEM -> SystemMessage.from(entity.getText());
            case AI -> {
                String payload = entity.getAiMessagePayloadJson();
                if (payload == null) {
                    throw new IllegalStateException(
                            "AI message row missing ai_message_payload_json; conversationId="
                                    + entity.getConversationId()
                                    + ", messageIndex=" + entity.getMessageIndex());
                }
                yield (AiMessage) ChatMessageDeserializer.messageFromJson(payload);
            }
            case TOOL_EXECUTION_RESULT -> ToolExecutionResultMessage.from(
                    entity.getToolCallId(),
                    entity.getToolName(),
                    entity.getText());
        };
    }

    /**
     * 提取 UserMessage 的单一文本;多模态时显式拒绝。
     *
     * <p>M1 阶段 ChatController 只发单文本,故所有合法 UserMessage 都应有恰好一个 TextContent。
     * 这一约束在 M6+ 多模态 RAG 启动时需要重审(本方法是显式扩展提醒点)。
     */
    private static String extractSingleText(UserMessage um) {
        if (um.contents().size() != 1 || !(um.contents().get(0) instanceof TextContent)) {
            throw new UnsupportedOperationException(
                    "Multimodal UserMessage not supported in M1 (contents size = "
                            + um.contents().size() + "). Implement when M6+ multi-modal RAG lands.");
        }
        return um.singleText();
    }
}
