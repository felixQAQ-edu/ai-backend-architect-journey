package com.felix.chatpipeline.billing;

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
 * billing_log 表 schema 形状验证。
 *
 * 这个测试在 Day 2 之前看似冗余(SQL 自己跑通了不就行?),但它的真正价值在 Day 2 之后:
 * 当 BillingLog Entity 字段与表列产生偏差时,这个测试会比应用启动失败更快地暴露问题。
 * 是后续所有 billing 相关变更的回归基线。
 */
@SpringBootTest
class BillingLogSchemaTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void billing_log_table_should_have_all_expected_columns() throws Exception {
        Set<String> columns = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, "BILLING_LOG", null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toUpperCase());
            }
        }

        assertThat(columns).containsExactlyInAnyOrder(
                "ID", "REQUEST_ID", "SESSION_ID", "USER_ID",
                "PROVIDER", "MODEL_NAME",
                "INPUT_TOKENS", "OUTPUT_TOKENS", "TOTAL_TOKENS",
                "INPUT_UNIT_PRICE", "OUTPUT_UNIT_PRICE", "TOTAL_COST", "CURRENCY",
                "STARTED_AT", "COMPLETED_AT", "LATENCY_MS",
                "STATUS", "ERROR_MESSAGE", "CREATED_AT"
        );
    }
}