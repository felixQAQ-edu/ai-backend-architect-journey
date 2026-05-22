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

/**
 * BillingLog 的 Repository 集成测试,验证 JPA 字段映射 + Flyway migration 在主库上的联合正确性。
 *
 * <p><b>设计选择说明</b>(看到 {@code @AutoConfigureTestDatabase(replace = Replace.NONE)}
 * 不要困惑,这是 Day 2 有意为之):
 * <ul>
 *   <li>{@code @DataJpaTest} 默认会用 embedded H2 替换主 DataSource,但我们关掉这个替换,
 *       让测试跑在主库 {@code ./data/learn} 上,这样能验证"V1+V2+V3 Flyway migration
 *       在真实文件库上跑出来的 schema 与 Entity 字段映射 100% 对齐"——比 in-memory
 *       默认替换的检查粒度更严</li>
 *   <li><b>安全垫底</b>:{@code @DataJpaTest} 默认每个 {@code @Test} 自动加
 *       {@code @Transactional},方法结束时自动回滚 INSERT,所以即使打到主库也不会污染数据
 *       (测试日志里能看到 INSERT 真的发出去,但事务回滚不留痕)</li>
 *   <li><b>互补关系</b>:{@code ConversationMessageSchemaTest} 用 {@code @SpringBootTest}
 *       默认加载 {@code test/application.yml},跑在 in-memory {@code mem:test} 库——
 *       两个角度互补:本测试压主库 schema + Flyway 联合,Schema 测试压列结构形状</li>
 * </ul>
 *
 * <p><b>已知 gotcha</b>:本测试不能在 {@code spring-boot:run} 跑着的时候并发运行——
 * H2 file mode 默认是独占的,会撞 {@code DbLockException}。如果一定要并发,把主库改成
 * server mode,或者先停掉 spring-boot 进程。
 */
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
        log.setConversationId("conv-xyz-456");
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
        assertThat(retrieved.getConversationId()).isEqualTo("conv-xyz-456");
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
