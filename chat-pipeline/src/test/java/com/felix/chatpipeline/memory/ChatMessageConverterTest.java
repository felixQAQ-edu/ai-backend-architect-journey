package com.felix.chatpipeline.memory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ChatMessage ↔ ConversationMessage 双向转换的单元测试。
 *
 * <p>覆盖 4 种 role 的 roundtrip + 已知异常路径(多模态拒绝、AI payload 缺失)。
 * 是后续 Step 4(PersistentChatMemoryStore)的回归基线——store 的 get/update
 * 实质就是"Repository CRUD + Converter 应用",Converter 正确 = store 正确的一半。
 */
class ChatMessageConverterTest {

    private final ChatMessageConverter converter = new ChatMessageConverter();

    @Test
    void user_message_text_roundtrip() {
        UserMessage original = UserMessage.from("Hello, world");

        ConversationMessage entity = converter.toEntity("conv-1", 0, original);

        assertThat(entity.getRole()).isEqualTo(MessageRole.USER);
        assertThat(entity.getText()).isEqualTo("Hello, world");
        assertThat(entity.getAiMessagePayloadJson()).isNull();
        assertThat(entity.getToolCallId()).isNull();
        assertThat(entity.getToolName()).isNull();

        ChatMessage rebuilt = converter.toChatMessage(entity);
        assertThat(rebuilt).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) rebuilt).singleText()).isEqualTo("Hello, world");
    }

    @Test
    void system_message_text_roundtrip() {
        SystemMessage original = SystemMessage.from("You are a helpful assistant.");

        ConversationMessage entity = converter.toEntity("conv-1", 0, original);

        assertThat(entity.getRole()).isEqualTo(MessageRole.SYSTEM);
        assertThat(entity.getText()).isEqualTo("You are a helpful assistant.");
        assertThat(entity.getAiMessagePayloadJson()).isNull();

        ChatMessage rebuilt = converter.toChatMessage(entity);
        assertThat(rebuilt).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) rebuilt).text()).isEqualTo("You are a helpful assistant.");
    }

    @Test
    void ai_message_plain_text_roundtrip() {
        AiMessage original = AiMessage.from("Sure, here is the answer.");

        ConversationMessage entity = converter.toEntity("conv-1", 1, original);

        assertThat(entity.getRole()).isEqualTo(MessageRole.AI);
        assertThat(entity.getText()).isEqualTo("Sure, here is the answer.");
        assertThat(entity.getAiMessagePayloadJson()).isNotBlank();

        ChatMessage rebuilt = converter.toChatMessage(entity);
        assertThat(rebuilt).isInstanceOf(AiMessage.class);
        AiMessage rebuiltAi = (AiMessage) rebuilt;
        assertThat(rebuiltAi.text()).isEqualTo("Sure, here is the answer.");
        assertThat(rebuiltAi.hasToolExecutionRequests()).isFalse();
    }

    @Test
    void ai_message_with_tool_calls_roundtrip() {
        ToolExecutionRequest req = ToolExecutionRequest.builder()
                .id("call_abc123")
                .name("getWeather")
                .arguments("{\"city\":\"Tokyo\"}")
                .build();
        AiMessage original = AiMessage.from(List.of(req));

        ConversationMessage entity = converter.toEntity("conv-2", 1, original);

        assertThat(entity.getRole()).isEqualTo(MessageRole.AI);
        // 纯 tool call 的 AiMessage,text() 是 null
        assertThat(entity.getText()).isNull();
        // payload 应包含 tool call 关键信息
        assertThat(entity.getAiMessagePayloadJson())
                .contains("call_abc123", "getWeather", "Tokyo");

        ChatMessage rebuilt = converter.toChatMessage(entity);
        AiMessage rebuiltAi = (AiMessage) rebuilt;
        assertThat(rebuiltAi.hasToolExecutionRequests()).isTrue();
        assertThat(rebuiltAi.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest rebuiltReq = rebuiltAi.toolExecutionRequests().get(0);
        assertThat(rebuiltReq.id()).isEqualTo("call_abc123");
        assertThat(rebuiltReq.name()).isEqualTo("getWeather");
        assertThat(rebuiltReq.arguments()).contains("Tokyo");
    }

    @Test
    void tool_execution_result_roundtrip() {
        ToolExecutionResultMessage original = ToolExecutionResultMessage.from(
                "call_abc123",
                "getWeather",
                "It is sunny in Tokyo.");

        ConversationMessage entity = converter.toEntity("conv-2", 2, original);

        assertThat(entity.getRole()).isEqualTo(MessageRole.TOOL_EXECUTION_RESULT);
        assertThat(entity.getText()).isEqualTo("It is sunny in Tokyo.");
        assertThat(entity.getToolCallId()).isEqualTo("call_abc123");
        assertThat(entity.getToolName()).isEqualTo("getWeather");
        assertThat(entity.getAiMessagePayloadJson()).isNull();

        ChatMessage rebuilt = converter.toChatMessage(entity);
        assertThat(rebuilt).isInstanceOf(ToolExecutionResultMessage.class);
        ToolExecutionResultMessage rebuiltTerm = (ToolExecutionResultMessage) rebuilt;
        assertThat(rebuiltTerm.id()).isEqualTo("call_abc123");
        assertThat(rebuiltTerm.toolName()).isEqualTo("getWeather");
        assertThat(rebuiltTerm.text()).isEqualTo("It is sunny in Tokyo.");
    }

    @Test
    void multimodal_user_message_should_be_rejected() {
        UserMessage multimodal = UserMessage.from(
                TextContent.from("Describe this image"),
                ImageContent.from("https://example.com/cat.jpg")
        );

        assertThatThrownBy(() -> converter.toEntity("conv-3", 0, multimodal))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Multimodal UserMessage not supported in M1");
    }

    @Test
    void ai_message_entity_missing_payload_should_throw() {
        ConversationMessage entity = new ConversationMessage();
        entity.setConversationId("conv-x");
        entity.setMessageIndex(0);
        entity.setRole(MessageRole.AI);
        entity.setText("partial text");
        // ai_message_payload_json deliberately left null —— simulating data corruption
        // or manual DB tampering

        assertThatThrownBy(() -> converter.toChatMessage(entity))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing ai_message_payload_json")
                .hasMessageContaining("conv-x");
    }
}
