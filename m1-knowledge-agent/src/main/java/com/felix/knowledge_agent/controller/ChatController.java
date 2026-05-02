package com.felix.knowledge_agent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SSE 流式输出控制器。
 *
 * <p>设计依据：ADR-001（SSE + Spring MVC + SseEmitter）。
 * 当前阶段为 mock 实现，M1 后续 task 会接入真实 LLM 调用。</p>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    /**
     * SSE 推送任务的工作线程池。
     *
     * <p>这里使用虚拟线程 (Java 21) 是 SseEmitter 的发送侧实现选择，
     * 与 ADR-001 关于"Tomcat 请求线程模型是否启用虚拟线程"是两个独立维度：
     * <ul>
     *   <li>ADR-001 决定的是入口请求线程（spring.threads.virtual.enabled），暂未开启</li>
     *   <li>这里使用虚拟线程做异步推送 worker，避免阻塞 Tomcat 请求线程，是局部最优</li>
     * </ul>
     */
    private final ExecutorService sseWorkerPool =
            Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Mock SSE 端点：模拟 LLM 流式输出，每 80ms 推一个字符。
     *
     * <p>验证方式：
     * <pre>
     *   curl -N "http://localhost:8080/api/chat/stream?q=Spring%20Boot"
     * </pre>
     * 期望看到字符逐个到达，最后一行 event: done。</p>
     *
     * @param q 用户提问，默认 "Hello"
     * @return SSE 事件流
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(defaultValue = "Hello") String q) {
        log.debug("Received SSE stream request, q={}", q);

        // timeout=0L 表示由业务方主动 complete()，不强制超时
        SseEmitter emitter = new SseEmitter(0L);

        sseWorkerPool.execute(() -> {
            try {
                String mockReply = "你问的是【" + q + "】，这是一段 mock 流式输出。";
                for (char ch : mockReply.toCharArray()) {
                    emitter.send(SseEmitter.event()
                            .name("token")
                            .data(String.valueOf(ch)));
                    Thread.sleep(80);
                }
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
                log.debug("SSE stream completed for q={}", q);
            } catch (IOException | InterruptedException e) {
                log.error("SSE stream failed for q={}", q, e);
                emitter.completeWithError(e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        return emitter;
    }
}