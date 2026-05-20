package com.felix.chatpipeline.billing;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;

/**
 * 把 ChatModelListener.onError 拿到的 Throwable 映射到 BillingStatus。
 *
 * 设计要点:
 *   - 用 instanceof 判断而非类名字符串比较,避免 LangChain4j 升级时类名变动导致失效
 *   - 顺序敏感:具体类型必须在通用类型之前判断
 *     (RateLimitException 可能 extends HttpException,具体在前才不会被吞)
 *   - 兜底返回 FAILED,任何未预期异常都不会让 listener 崩
 */
public final class BillingStatusClassifier {

    private BillingStatusClassifier() {}

    public static BillingStatus classify(Throwable error) {
        if (error == null) {
            return BillingStatus.FAILED;
        }
        // 具体 → 通用
        if (error instanceof RateLimitException) {
            return BillingStatus.RATE_LIMITED;
        }
        if (error instanceof AuthenticationException
                || error instanceof InvalidRequestException
                || error instanceof InternalServerException
                || error instanceof ContentFilteredException
                || error instanceof ModelNotFoundException
                || error instanceof HttpException) {
            return BillingStatus.ERROR_RESPONSE;
        }
        // UnresolvedModelServerException / TimeoutException / 其他都归 FAILED
        return BillingStatus.FAILED;
    }
}