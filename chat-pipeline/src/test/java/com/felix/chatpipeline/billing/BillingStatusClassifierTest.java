package com.felix.chatpipeline.billing;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillingStatusClassifierTest {

    @Test
    void rateLimitException_mapsTo_RATE_LIMITED() {
        assertThat(BillingStatusClassifier.classify(new RateLimitException("429")))
                .isEqualTo(BillingStatus.RATE_LIMITED);
    }

    @Test
    void authenticationException_mapsTo_ERROR_RESPONSE() {
        assertThat(BillingStatusClassifier.classify(new AuthenticationException("401")))
                .isEqualTo(BillingStatus.ERROR_RESPONSE);
    }

    @Test
    void invalidRequestException_mapsTo_ERROR_RESPONSE() {
        assertThat(BillingStatusClassifier.classify(new InvalidRequestException("400")))
                .isEqualTo(BillingStatus.ERROR_RESPONSE);
    }

    @Test
    void internalServerException_mapsTo_ERROR_RESPONSE() {
        assertThat(BillingStatusClassifier.classify(new InternalServerException("500")))
                .isEqualTo(BillingStatus.ERROR_RESPONSE);
    }

    @Test
    void unresolvedModelServerException_mapsTo_FAILED() {
        // ← 你刚才 SmokeTest 拿到的真实样本类型
        assertThat(BillingStatusClassifier.classify(new UnresolvedModelServerException("dns fail")))
                .isEqualTo(BillingStatus.FAILED);
    }

    @Test
    void timeoutException_mapsTo_FAILED() {
        assertThat(BillingStatusClassifier.classify(new TimeoutException("timeout")))
                .isEqualTo(BillingStatus.FAILED);
    }

    @Test
    void unknownRuntimeException_mapsTo_FAILED() {
        assertThat(BillingStatusClassifier.classify(new RuntimeException("???")))
                .isEqualTo(BillingStatus.FAILED);
    }

    @Test
    void nullError_mapsTo_FAILED() {
        assertThat(BillingStatusClassifier.classify(null))
                .isEqualTo(BillingStatus.FAILED);
    }
}