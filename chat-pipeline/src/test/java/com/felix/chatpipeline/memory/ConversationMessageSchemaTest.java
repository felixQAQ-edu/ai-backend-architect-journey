package com.felix.chatpipeline.memory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * conversation_message 表 schema 形状验证。
 *
 * 与 BillingLogSchemaTest 同样的设计意图:当 ConversationMessage Entity 字段
 * 与表列产生偏差时,这个测试比应用启动失败更快暴露问题。是后续所有 ChatMemoryStore
 * 相关变更(Step 3 转换器 / Step 4 store 实现)的回归基线。
 */
@SpringBootTest
class ConversationMessageSchemaTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void conversation_message_table_should_have_all_expected_columns() throws Exception {
        Set<String> columns = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, "CONVERSATION_MESSAGE", null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toUpperCase());
            }
        }

        assertThat(columns).containsExactlyInAnyOrder(
                "ID",
                "CONVERSATION_ID", "MESSAGE_INDEX",
                "ROLE", "TEXT",
                "AI_MESSAGE_PAYLOAD_JSON", "TOOL_CALL_ID", "TOOL_NAME",
                "CREATED_AT"
        );
    }
}
