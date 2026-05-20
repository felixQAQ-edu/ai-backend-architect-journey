package com.felix.chatpipeline.billing;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BillingListenerSmokeTest {

    @Autowired
    private StreamingChatModel streamingChatModel;

    @Autowired
    private BillingLogRepository billingLogRepository;

    @Test
    void shouldTriggerListenerCallbacks() throws InterruptedException {
        // ★ 此测试不走 HTTP 层,Filter 不触发,需要手动塞 MDC 模拟请求入口
        String requestId = "test-req-" + UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);
        System.out.println("[测试] 注入 requestId=" + requestId);

        try {
            CountDownLatch done = new CountDownLatch(1);

            streamingChatModel.chat(
                    ChatRequest.builder()
                            .messages(UserMessage.from("说一句 ping,五个字以内"))
                            .build(),
                    new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String partialResponse) {
                            System.out.print(partialResponse);
                        }

                        @Override
                        public void onCompleteResponse(ChatResponse completeResponse) {
                            System.out.println();
                            System.out.println("[测试] 完成 | thread=" + Thread.currentThread().getName()
                                    + " | tokenUsage=" + completeResponse.tokenUsage());
                            done.countDown();
                        }

                        @Override
                        public void onError(Throwable error) {
                            error.printStackTrace();
                            System.err.println("[测试] 错误 | thread=" + Thread.currentThread().getName()
                                    + " | " + error.getClass().getSimpleName() + ": " + error.getMessage());
                            done.countDown();
                        }
                    }
            );

            boolean completed = done.await(30, TimeUnit.SECONDS);
            if (!completed) {
                throw new AssertionError("LLM 流式调用 30 秒超时");
            }

            // ============ Step 4b 端到端断言:BillingLog 必须已经落库 ============
            // listener 的 save() 是异步线程跑的,主线程到这里时通常已经完成,
            // 但保险起见短轮询(避免偶发 race)。
            Optional<BillingLog> persisted = pollUntilPresent(requestId, 2000);

            assertThat(persisted)
                    .as("onError 必须把 BillingLog 落库,requestId=" + requestId)
                    .isPresent();

            BillingLog log = persisted.get();
            assertThat(log.getStatus()).isEqualTo(BillingStatus.FAILED);  // UnresolvedModelServerException → FAILED
            assertThat(log.getRequestId()).isEqualTo(requestId);
            assertThat(log.getInputTokens()).isZero();
            assertThat(log.getOutputTokens()).isZero();
            assertThat(log.getErrorMessage())
                    .as("errorMessage 应该包含异常类名")
                    .contains("UnresolvedModelServerException");
            assertThat(log.getLatencyMs()).isGreaterThanOrEqualTo(0);

            System.out.println("[测试] ✅ BillingLog 落库验证通过 | id=" + log.getId()
                    + " | status=" + log.getStatus()
                    + " | latencyMs=" + log.getLatencyMs());

        } finally {
            MDC.remove("requestId");
        }
    }

    /** 短轮询等待 listener 异步落库。最长等 maxWaitMs 毫秒,每 50ms 查一次。 */
    private Optional<BillingLog> pollUntilPresent(String requestId, long maxWaitMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            Optional<BillingLog> result = billingLogRepository.findByRequestId(requestId);
            if (result.isPresent()) {
                return result;
            }
            Thread.sleep(50);
        }
        return billingLogRepository.findByRequestId(requestId);
    }
}