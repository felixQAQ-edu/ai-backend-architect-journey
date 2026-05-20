package com.felix.chatpipeline.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
class RequestIdFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldGenerateRequestIdWhenNotProvided() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/health"))
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();

        String generated = result.getResponse().getHeader("X-Request-Id");
        assertThat(generated)
                .isNotBlank()
                .matches("^[0-9a-f-]{36}$");  // UUID 格式
    }

    @Test
    void shouldEchoClientProvidedRequestId() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("X-Request-Id", "client-supplied-12345"))
                .andExpect(header().string("X-Request-Id", "client-supplied-12345"));
    }
}