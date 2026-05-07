package com.felix.chatpipeline.controller;

import com.felix.chatpipeline.assistant.KnowledgeAssistant;
import com.felix.chatpipeline.dto.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 流式对话端点。
 * <p>
 * ADR-001 决定：用 SSE + Spring MVC + SseEmitter，不用 WebFlux。
 * 这里不需要任何响应式 API，全程命令式 + 异步回调。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    /** SSE 连接超时（毫秒）。设短一点便于发现卡死，生产环境再调长 */
    private static final long SSE_TIMEOUT_MS = 60_000L;

    private final KnowledgeAssistant assistant;

    public ChatController(KnowledgeAssistant assistant) {
        this.assistant = assistant;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        log.info("[Chat] sessionId={} message={}", request.sessionId(), request.message());

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 客户端断连/超时清理钩子
        emitter.onTimeout(() -> log.warn("[SSE] timeout sessionId={}", request.sessionId()));
        emitter.onError(err -> log.warn("[SSE] error sessionId={} err={}",
                request.sessionId(), err.getMessage()));

        try {
            assistant.chat(request.sessionId(), request.message())

                    // 1) 每个 token 推一次
                    .onPartialResponse(token -> sendEvent(emitter, "token", token))

                    // 2) Tool 执行完成的中间事件 —— 前端可以用这个展示"正在调用 xxx 工具"
                    .onToolExecuted(execution -> {
                        String payload = """
                                {"name":"%s","args":%s,"result":%s}
                                """.formatted(
                                escape(execution.request().name()),
                                jsonString(execution.request().arguments()),
                                jsonString(execution.result())
                        );
                        sendEvent(emitter, "tool", payload);
                    })

                    // 3) 完整响应到达 —— 这里能拿到 token usage，是计费中间件的切入点
                    .onCompleteResponse(response -> {
                        // TODO(M1): Token 计费中间件接入点
                        //   var usage = response.tokenUsage();
                        //   billingService.record(request.sessionId(),
                        //       usage.inputTokenCount(), usage.outputTokenCount());
                        log.info("[Chat] complete sessionId={} tokenUsage={}",
                                request.sessionId(), response.tokenUsage());
                        sendEvent(emitter, "done", "");
                        emitter.complete();
                    })

                    // 4) 错误回调
                    .onError(err -> {
                        log.error("[Chat] error sessionId={}", request.sessionId(), err);
                        sendEvent(emitter, "error", escape(err.getMessage()));
                        emitter.completeWithError(err);
                    })

                    // 5) 启动异步流（这里立即返回，SSE emitter 由后台线程持续 send）
                    .start();

        } catch (Exception e) {
            log.error("[Chat] failed to start stream", e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /** SSE 单事件发送，吞掉 IOException（客户端断连时 send 会抛） */
    private void sendEvent(SseEmitter emitter, String name, String data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            log.debug("[SSE] client disconnected during send name={}", name);
            // 不再 try 后续 send；onError 会接管清理
        }
    }

    /** JSON 字符串值的最小化转义（避免引入 Jackson 依赖做这么小的事） */
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    private String jsonString(String raw) {
        return "\"" + escape(raw) + "\"";
    }
}
