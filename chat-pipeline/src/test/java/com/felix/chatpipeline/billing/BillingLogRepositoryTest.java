package com.felix.chatpipeline.billing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class BillingLogRepositoryTest {

    @Autowired
    private BillingLogRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void saveAndFindById_preservesAllBusinessFields() {
        // ---------- Arrange ----------
        BillingLog log = new BillingLog();
        log.setRequestId("req-abc-123");
        log.setSessionId("sess-xyz-456");
        log.setUserId("user-felix");
        log.setProvider("openai");
        log.setModelName("gpt-4o-mini");
        log.setInputTokens(1500);
        log.setOutputTokens(800);
        log.setTotalTokens(2300);
        log.setInputUnitPrice(new BigDecimal("0.0000020000"));
        log.setOutputUnitPrice(new BigDecimal("0.0000080000"));
        log.setTotalCost(new BigDecimal("0.009400"));
        log.setCurrency("CNY");
        log.setStartedAt(Instant.parse("2026-05-11T10:00:00Z"));
        log.setCompletedAt(Instant.parse("2026-05-11T10:00:02.500Z"));
        log.setLatencyMs(2500);
        log.setStatus(BillingStatus.SUCCESS);
        log.setErrorMessage(null); // success 场景没有错误信息

        // ---------- Act ----------
        BillingLog saved = repository.save(log);
        entityManager.flush();  // 强制 SQL 真正发到 DB(INSERT 执行)
        entityManager.clear();  // 清掉一级缓存,下一行 findById 必须从 DB 重新加载

        // ---------- Assert ----------
        assertThat(saved.getId()).as("save 后主键应被回填").isNotNull();

        Optional<BillingLog> retrievedOpt = repository.findById(saved.getId());
        assertThat(retrievedOpt).isPresent();

        BillingLog retrieved = retrievedOpt.get();
        assertThat(retrieved)
                .as("证明真的从 DB 重新加载,不是 persistence context 里的同一引用")
                .isNotSameAs(saved);

        // 业务字段(18 个)逐一断言
        assertThat(retrieved.getRequestId()).isEqualTo("req-abc-123");
        assertThat(retrieved.getSessionId()).isEqualTo("sess-xyz-456");
        assertThat(retrieved.getUserId()).isEqualTo("user-felix");
        assertThat(retrieved.getProvider()).isEqualTo("openai");
        assertThat(retrieved.getModelName()).isEqualTo("gpt-4o-mini");
        assertThat(retrieved.getInputTokens()).isEqualTo(1500);
        assertThat(retrieved.getOutputTokens()).isEqualTo(800);
        assertThat(retrieved.getTotalTokens()).isEqualTo(2300);
        // BigDecimal 用 isEqualByComparingTo 而不是 isEqualTo,原因见下面说明
        assertThat(retrieved.getInputUnitPrice()).isEqualByComparingTo("0.0000020000");
        assertThat(retrieved.getOutputUnitPrice()).isEqualByComparingTo("0.0000080000");
        assertThat(retrieved.getTotalCost()).isEqualByComparingTo("0.009400");
        assertThat(retrieved.getCurrency()).isEqualTo("CNY");
        assertThat(retrieved.getStartedAt()).isEqualTo(Instant.parse("2026-05-11T10:00:00Z"));
        assertThat(retrieved.getCompletedAt()).isEqualTo(Instant.parse("2026-05-11T10:00:02.500Z"));
        assertThat(retrieved.getLatencyMs()).isEqualTo(2500);
        assertThat(retrieved.getStatus()).isEqualTo(BillingStatus.SUCCESS);
        assertThat(retrieved.getErrorMessage()).isNull();

        // @CreationTimestamp 字段应在 INSERT 时被 Hibernate 自动填值
        assertThat(retrieved.getCreatedAt())
                .as("@CreationTimestamp 应自动填值")
                .isNotNull();
    }

    @Test
    void findByRequestId_returnsEntity_whenPresent() {
        BillingLog log = sampleLog("req-find-test");
        repository.save(log);
        entityManager.flush();
        entityManager.clear();

        Optional<BillingLog> found = repository.findByRequestId("req-find-test");

        assertThat(found).isPresent();
        assertThat(found.get().getRequestId()).isEqualTo("req-find-test");
    }

    @Test
    void findByRequestId_returnsEmpty_whenAbsent() {
        Optional<BillingLog> found = repository.findByRequestId("non-existent-id");
        assertThat(found).isEmpty();
    }

    /** 测试用最小有效 BillingLog,只填 NOT NULL 字段,可选字段留默认/null。 */
    private BillingLog sampleLog(String requestId) {
        BillingLog log = new BillingLog();
        log.setRequestId(requestId);
        log.setProvider("openai");
        log.setModelName("gpt-4o-mini");
        log.setInputTokens(100);
        log.setOutputTokens(50);
        log.setTotalTokens(150);
        log.setInputUnitPrice(new BigDecimal("0.0000020000"));
        log.setOutputUnitPrice(new BigDecimal("0.0000080000"));
        log.setTotalCost(new BigDecimal("0.000600"));
        log.setStartedAt(Instant.parse("2026-05-11T10:00:00Z"));
        log.setCompletedAt(Instant.parse("2026-05-11T10:00:01Z"));
        log.setLatencyMs(1000);
        log.setStatus(BillingStatus.SUCCESS);
        return log;
    }
}