package com.felix.chatpipeline.billing;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Token 计费 Listener。跨线程上下文传递详见 ADR-006 + LEARNING-NOTES 笔记 4。
 * Step 3 (SUCCESS) / Step 4 (FAILED 系列) 落库路径都在这里。
 */
@Component
public class BillingListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(BillingListener.class);

    static final String REQUEST_ID_KEY = "requestId";
    static final String STARTED_AT_KEY = "startedAt";

    private static final String DEFAULT_PROVIDER = "openai";
    private static final String UNKNOWN_MODEL = "unknown";
    private static final int ERROR_MESSAGE_MAX_LEN = 1000;

    private final BillingLogRepository repository;

    public BillingListener(BillingLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        String requestId = MDC.get(REQUEST_ID_KEY);
        if (requestId != null) {
            requestContext.attributes().put(REQUEST_ID_KEY, requestId);
        }
        requestContext.attributes().put(STARTED_AT_KEY, Instant.now());

        log.info("[BillingListener] onRequest  | thread={} | requestId={} | model={}",
                Thread.currentThread().getName(),
                requestId,
                safeModelNameFromRequest(requestContext));
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        String requestId = (String) responseContext.attributes().get(REQUEST_ID_KEY);
        Instant startedAt = (Instant) responseContext.attributes().get(STARTED_AT_KEY);
        boolean mdcInjectedHere = injectMdc(requestId);
        try {
            TokenUsage tokenUsage = responseContext.chatResponse().tokenUsage();
            log.info("[BillingListener] onResponse | thread={} | requestId={} | tokenUsage={}",
                    Thread.currentThread().getName(), requestId, tokenUsage);

            try {
                BillingLog entry = buildSuccessLog(responseContext, requestId, startedAt, tokenUsage);
                repository.save(entry);
                log.info("[BillingListener] BillingLog 已落库 | id={} | requestId={} | status={} | latencyMs={}",
                        entry.getId(), entry.getRequestId(), entry.getStatus(), entry.getLatencyMs());
            } catch (Exception saveErr) {
                log.error("[BillingListener] BillingLog 落库失败 | requestId={} | reason={}",
                        requestId, saveErr.getMessage(), saveErr);
            }
        } finally {
            if (mdcInjectedHere) {
                MDC.remove(REQUEST_ID_KEY);
            }
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        String requestId = (String) errorContext.attributes().get(REQUEST_ID_KEY);
        Instant startedAt = (Instant) errorContext.attributes().get(STARTED_AT_KEY);
        boolean mdcInjectedHere = injectMdc(requestId);
        try {
            Throwable error = errorContext.error();
            BillingStatus status = BillingStatusClassifier.classify(error);

            log.warn("[BillingListener] onError    | thread={} | requestId={} | status={} | error={} | message={}",
                    Thread.currentThread().getName(), requestId, status,
                    error.getClass().getSimpleName(), error.getMessage());

            try {
                BillingLog entry = buildErrorLog(errorContext, requestId, startedAt, status, error);
                repository.save(entry);
                log.info("[BillingListener] BillingLog 已落库 | id={} | requestId={} | status={}",
                        entry.getId(), entry.getRequestId(), entry.getStatus());
            } catch (Exception saveErr) {
                log.error("[BillingListener] BillingLog 落库失败 | requestId={} | reason={}",
                        requestId, saveErr.getMessage(), saveErr);
            }
        } finally {
            if (mdcInjectedHere) {
                MDC.remove(REQUEST_ID_KEY);
            }
        }
    }

    // ---------- helpers ----------

    private BillingLog buildSuccessLog(ChatModelResponseContext ctx,
                                       String requestId,
                                       Instant startedAt,
                                       TokenUsage tokenUsage) {
        BillingLog entry = newBaseLog(requestId);
        entry.setProvider(DEFAULT_PROVIDER);
        entry.setModelName(coalesce(safeModelNameFromResponse(ctx), UNKNOWN_MODEL));

        int inputTokens = tokenUsage != null && tokenUsage.inputTokenCount() != null
                ? tokenUsage.inputTokenCount() : 0;
        int outputTokens = tokenUsage != null && tokenUsage.outputTokenCount() != null
                ? tokenUsage.outputTokenCount() : 0;
        int totalTokens = tokenUsage != null && tokenUsage.totalTokenCount() != null
                ? tokenUsage.totalTokenCount() : inputTokens + outputTokens;
        entry.setInputTokens(inputTokens);
        entry.setOutputTokens(outputTokens);
        entry.setTotalTokens(totalTokens);

        // 单价暂设 0(M2 引入实际单价表,按 provider × model 计费)
        entry.setInputUnitPrice(BigDecimal.ZERO);
        entry.setOutputUnitPrice(BigDecimal.ZERO);
        entry.setTotalCost(BigDecimal.ZERO);
        entry.setCurrency("CNY");

        fillTimings(entry, startedAt);
        entry.setStatus(BillingStatus.SUCCESS);
        entry.setErrorMessage(null);
        return entry;
    }

    private BillingLog buildErrorLog(ChatModelErrorContext ctx,
                                     String requestId,
                                     Instant startedAt,
                                     BillingStatus status,
                                     Throwable error) {
        BillingLog entry = newBaseLog(requestId);
        entry.setProvider(DEFAULT_PROVIDER);
        entry.setModelName(coalesce(safeModelNameFromError(ctx), UNKNOWN_MODEL));

        entry.setInputTokens(0);
        entry.setOutputTokens(0);
        entry.setTotalTokens(0);
        entry.setInputUnitPrice(BigDecimal.ZERO);
        entry.setOutputUnitPrice(BigDecimal.ZERO);
        entry.setTotalCost(BigDecimal.ZERO);
        entry.setCurrency("CNY");

        fillTimings(entry, startedAt);
        entry.setStatus(status);
        String errMsg = error.getClass().getName() + ": "
                + (error.getMessage() != null ? error.getMessage() : "(no message)");
        entry.setErrorMessage(truncate(errMsg, ERROR_MESSAGE_MAX_LEN));
        return entry;
    }

    private BillingLog newBaseLog(String requestId) {
        BillingLog entry = new BillingLog();
        entry.setRequestId(requestId != null
                ? requestId
                : "orphan-" + UUID.randomUUID().toString().substring(0, 8));
        return entry;
    }

    private void fillTimings(BillingLog entry, Instant startedAt) {
        Instant now = Instant.now();
        Instant effectiveStart = startedAt != null ? startedAt : now;
        entry.setStartedAt(effectiveStart);
        entry.setCompletedAt(now);
        entry.setLatencyMs((int) Math.max(0, Duration.between(effectiveStart, now).toMillis()));
    }

    private boolean injectMdc(String requestId) {
        if (requestId != null && MDC.get(REQUEST_ID_KEY) == null) {
            MDC.put(REQUEST_ID_KEY, requestId);
            return true;
        }
        return false;
    }

    private static String safeModelNameFromRequest(ChatModelRequestContext ctx) {
        try { return ctx.chatRequest().modelName(); } catch (Exception e) { return null; }
    }

    private static String safeModelNameFromResponse(ChatModelResponseContext ctx) {
        try { return ctx.chatRequest().modelName(); } catch (Exception e) { return null; }
    }

    private static String safeModelNameFromError(ChatModelErrorContext ctx) {
        try { return ctx.chatRequest().modelName(); } catch (Exception e) { return null; }
    }

    private static String coalesce(String a, String b) {
        return a != null ? a : b;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}