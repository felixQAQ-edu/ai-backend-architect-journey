package com.felix.chatpipeline.billing;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "billing_log")
public class BillingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String requestId;

    @Column(length = 64)
    private String sessionId;

    @Column(length = 64)
    private String userId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 64)
    private String modelName;

    @Column(nullable = false)
    private int inputTokens;

    @Column(nullable = false)
    private int outputTokens;

    @Column(nullable = false)
    private int totalTokens;

    @Column(nullable = false, precision = 20, scale = 10)
    private BigDecimal inputUnitPrice;

    @Column(nullable = false, precision = 20, scale = 10)
    private BigDecimal outputUnitPrice;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal totalCost;

    @Column(nullable = false, length = 8)
    private String currency = "CNY";

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant completedAt;

    @Column(nullable = false)
    private int latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BillingStatus status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public BillingLog() {
        // JPA requires no-arg constructor
    }

    // ---------- getters & setters ----------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public int getInputTokens() { return inputTokens; }
    public void setInputTokens(int inputTokens) { this.inputTokens = inputTokens; }

    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }

    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

    public BigDecimal getInputUnitPrice() { return inputUnitPrice; }
    public void setInputUnitPrice(BigDecimal inputUnitPrice) { this.inputUnitPrice = inputUnitPrice; }

    public BigDecimal getOutputUnitPrice() { return outputUnitPrice; }
    public void setOutputUnitPrice(BigDecimal outputUnitPrice) { this.outputUnitPrice = outputUnitPrice; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public int getLatencyMs() { return latencyMs; }
    public void setLatencyMs(int latencyMs) { this.latencyMs = latencyMs; }

    public BillingStatus getStatus() { return status; }
    public void setStatus(BillingStatus status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    // 注意:没有 setCreatedAt,@CreationTimestamp 自己填,业务代码不许动

    // ---------- equals & hashCode (业务键) ----------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BillingLog that)) return false;
        return Objects.equals(requestId, that.requestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId);
    }

    @Override
    public String toString() {
        return "BillingLog{id=" + id +
                ", requestId='" + requestId + '\'' +
                ", provider='" + provider + '\'' +
                ", model='" + modelName + '\'' +
                ", totalTokens=" + totalTokens +
                ", totalCost=" + totalCost +
                ", status=" + status +
                '}';
    }
}