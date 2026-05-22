package com.felix.chatpipeline.memory;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PersistentChatMemoryStore 集成测试。
 *
 * <p>覆盖 ADR-007 设计的三个核心场景:
 * <ul>
 *   <li>getMessages 空 / 有数据</li>
 *   <li>updateMessages 全量替换语义(第一次写 / 二次覆盖 / 含 tool calls 顺序保持)</li>
 *   <li>deleteMessages 清空 + 不同 conversation 隔离</li>
 * </ul>
 *
 * <p>用 {@code @Transactional} 让每个测试自动回滚,避免数据残留污染下一个测试。
 * 这也是 Repository 在 {@code @Modifying} 上加 flushAutomatically + clearAutomatically
 * 的原因——测试嵌套事务时,pending INSERT 必须在 DELETE 前被 flush,否则全量替换语义会失效。
 */
@SpringBootTest
@Transactional
class PersistentChatMemoryStoreIntegrationTest {

    @Autowired
    private PersistentChatMemoryStore store;

    @Test
    void get_messages_returns_empty_for_unknown_conversation() {
        List<ChatMessage> messages = store.getMessages("non-existent-conv");
        assertThat(messages).isEmpty();
    }

    @Test
    void update_messages_persists_and_reads_back() {
        String convId = "conv-update-1";
        List<ChatMessage> original = List.of(
                SystemMessage.from("You are a helpful assistant."),
                UserMessage.from("Hello"),
                AiMessage.from("Hi there!")
        );

        store.updateMessages(convId, original);

        List<ChatMessage> read = store.getMessages(convId);
        assertThat(read).hasSize(3);
        assertThat(read.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(((SystemMessage) read.get(0)).text()).isEqualTo("You are a helpful assistant.");
        assertThat(read.get(1)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) read.get(1)).singleText()).isEqualTo("Hello");
        assertThat(read.get(2)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) read.get(2)).text()).isEqualTo("Hi there!");
    }

    @Test
    void update_messages_replaces_old_data_not_append() {
        String convId = "conv-replace-1";

        // 第一次写 2 条
        store.updateMessages(convId, List.of(
                UserMessage.from("First message"),
                AiMessage.from("First reply")
        ));
        assertThat(store.getMessages(convId)).hasSize(2);

        // 第二次写 1 条(模拟 evict 后场景)
        store.updateMessages(convId, List.of(
                UserMessage.from("Replacement only")
        ));

        // 应当只有 1 条,旧数据被清空 —— 这是 ADR-007"全量替换"语义的核心验证
        List<ChatMessage> read = store.getMessages(convId);
        assertThat(read).hasSize(1);
        assertThat(((UserMessage) read.get(0)).singleText()).isEqualTo("Replacement only");
    }

    @Test
    void update_messages_preserves_order_with_tool_calls() {
        String convId = "conv-tool-1";

        ToolExecutionRequest req = ToolExecutionRequest.builder()
                .id("call_xyz")
                .name("getWeather")
                .arguments("{\"city\":\"Tokyo\"}")
                .build();

        List<ChatMessage> toolRoundtrip = List.of(
                UserMessage.from("What's the weather in Tokyo?"),
                AiMessage.from(List.of(req)),
                ToolExecutionResultMessage.from("call_xyz", "getWeather", "Sunny, 25°C"),
                AiMessage.from("It's sunny and 25°C in Tokyo.")
        );

        store.updateMessages(convId, toolRoundtrip);

        List<ChatMessage> read = store.getMessages(convId);
        assertThat(read).hasSize(4);

        // 顺序正确性(message_index 设计意图的核心验证)
        assertThat(read.get(0)).isInstanceOf(UserMessage.class);
        assertThat(read.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) read.get(1)).hasToolExecutionRequests()).isTrue();
        assertThat(read.get(2)).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(read.get(3)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) read.get(3)).text()).contains("sunny");
    }

    @Test
    void delete_messages_removes_all_rows_for_conversation() {
        String convId = "conv-delete-1";
        store.updateMessages(convId, List.of(
                UserMessage.from("Will be deleted"),
                AiMessage.from("OK")
        ));
        assertThat(store.getMessages(convId)).hasSize(2);

        store.deleteMessages(convId);

        assertThat(store.getMessages(convId)).isEmpty();
    }

    @Test
    void update_messages_for_different_conversations_are_isolated() {
        store.updateMessages("conv-A", List.of(UserMessage.from("Hello from A")));
        store.updateMessages("conv-B", List.of(UserMessage.from("Hello from B")));

        // delete A 不应影响 B
        store.deleteMessages("conv-A");
        assertThat(store.getMessages("conv-A")).isEmpty();
        assertThat(store.getMessages("conv-B")).hasSize(1);
        assertThat(((UserMessage) store.getMessages("conv-B").get(0)).singleText())
                .isEqualTo("Hello from B");
    }
}
