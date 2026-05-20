package com.felix.chatpipeline.billing;

/**
 * BillingLog 的状态分类(详见 CONTEXT.md):
 *   - SUCCESS:        LLM 调用成功(onResponse 路径)
 *   - RATE_LIMITED:   HTTP 429,provider 限流
 *   - ERROR_RESPONSE: HTTP 4xx/5xx,provider 业务错误(认证/参数/审核/模型不存在等)
 *   - FAILED:         网络/连接/超时/未知异常,兜底类
 */
public enum BillingStatus {
    SUCCESS,
    RATE_LIMITED,
    ERROR_RESPONSE,
    FAILED
}