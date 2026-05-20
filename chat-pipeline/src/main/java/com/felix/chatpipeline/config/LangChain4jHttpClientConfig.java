package com.felix.chatpipeline.config;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 用 JDK HttpClient 替换 starter 默认的 spring-restclient adapter。
 *
 * 背景:
 *   1) starter 默认装载 spring-restclient,1.11.0-beta19 streaming 路径会抛
 *      UnresolvedAddressException(详见 LEARNING-NOTES 笔记 6)。
 *   2) 切到 JDK HttpClient 后,JDK 自己的异步 HTTP/2 路径在 macOS + JDK 21
 *      上也会同症状抛错。需要显式 force HTTP/1.1 + NO_PROXY 来绕开。
 *
 * bean 名字必须精确叫 openAiStreamingChatModelHttpClientBuilder,
 * 借助 starter 的 @ConditionalOnMissingBean(names=...) 跳过 starter 默认装配。
 */
@Configuration
public class LangChain4jHttpClientConfig {

    @Bean
    public HttpClientBuilder openAiStreamingChatModelHttpClientBuilder() {
        HttpClient.Builder jdkBuilder = HttpClient.newBuilder()
                // 强制 HTTP/1.1,绕开 JDK 21 异步 HTTP/2 在 macOS 上的 UnresolvedAddressException
                .version(HttpClient.Version.HTTP_1_1)
                // 显式无代理,覆盖 JDK 默认 ProxySelector 在异步路径上的不确定行为
                .proxy(ProxySelector.of(null))
                .connectTimeout(Duration.ofSeconds(30));

        return JdkHttpClient.builder()
                .httpClientBuilder(jdkBuilder);
    }
}