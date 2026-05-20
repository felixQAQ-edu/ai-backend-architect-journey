package com.felix.chatpipeline.web;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Controller 层的横切关注点(详见 ADR-006 权威分工表):
 *   - @Around:enter/exit 日志 + setup timing
 *   - @AfterThrowing:Controller 异常兜底日志
 *
 * ⚠️ SSE 场景的 timing 含义(详见 ADR-001 + LEARNING-NOTES 笔记 3):
 *   SseEmitter 在 controller 方法内立即返回(~1ms),真正的 token 流推送发生在方法返回之后。
 *   所以 @Around 测出来的 controllerSetupMs 只是 "emitter 创建 + 启动异步流" 的时间,
 *   不是端到端流式响应耗时。LLM 调用本身的耗时由 ChatModelListener 测(onRequest → onResponse)。
 *
 * 跨切面读 MDC.requestId 是 OK 的,因为 Aspect 在调用线程(Tomcat worker)上跑,
 * 与 RequestIdFilter 装填 MDC 是同一线程。
 */
@Aspect
@Component
public class RequestLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingAspect.class);

    /** 切所有标注了 @RestController 的类的所有 public 方法。 */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void restControllerMethods() {}

    @Around("restControllerMethods()")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
        String methodSig = pjp.getSignature().toShortString();
        String requestId = MDC.get("requestId");
        long start = System.currentTimeMillis();

        log.info("[Aspect] → enter | method={} | requestId={}", methodSig, requestId);

        Object result = pjp.proceed();  // 异常会向上抛,由 @AfterThrowing 接管

        long setupMs = System.currentTimeMillis() - start;
        log.info("[Aspect] ← exit  | method={} | requestId={} | controllerSetupMs={} (SSE 场景仅含 emitter 创建)",
                methodSig, requestId, setupMs);
        return result;
    }

    @AfterThrowing(pointcut = "restControllerMethods()", throwing = "error")
    public void logAfterThrowing(JoinPoint jp, Throwable error) {
        log.error("[Aspect] ✗ error | method={} | requestId={} | error={} | message={}",
                jp.getSignature().toShortString(),
                MDC.get("requestId"),
                error.getClass().getSimpleName(),
                error.getMessage());
    }
}