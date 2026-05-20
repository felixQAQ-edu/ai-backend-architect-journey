package com.felix.chatpipeline.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * HTTP 请求入口注入 requestId,详见 CONTEXT.md。三个载体:
 *   - 请求头 X-Request-Id(客户端传入则复用,否则新生成)
 *   - 响应头 X-Request-Id(回传给前端/客户端追踪用)
 *   - MDC key=requestId(给同步日志、@Aspect、BillingListener.onRequest 读取)
 *
 * 必须在 finally 里清 MDC,否则 Tomcat 线程池复用线程时会污染下一个请求。
 * OncePerRequestFilter 保证一次请求只跑一次(forward / include 也安全)。
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }
}