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
 * <p><b>当前状态:保留观察</b>——本配置在 Day 3 排查 {@code UnresolvedAddressException}
 * 时引入(详见 LEARNING-NOTES 笔记 7),原假设是"JDK 21 异步路径 + spring-restclient
 * 有暗坑"。Day 4 末笔记 8 的反证链推翻了这一根因——真因是 IDEA Test Runner agent
 * 注入而非 JDK 本身或 HTTP client 实现。这意味着<b>本配置的必要性需要重新验证:
 * 可能其实可以删,可能不能</b>。
 *
 * <p><b>暂不删的原因</b>:
 * <ol>
 *   <li>当前在工作(Day 5 真实多轮对话验证通过)</li>
 *   <li>删除需要刻意的反证实验({@code spring-boot:run} + 真实 LLM key,删配置后
 *       看是否复现 {@code UnresolvedAddressException})</li>
 *   <li>Week 3 收尾时间紧张,留作 Week 4+ 暖机任务</li>
 * </ol>
 *
 * <p><b>Bean 名字必须精确叫</b> {@code openAiStreamingChatModelHttpClientBuilder},
 * 借助 starter 的 {@code @ConditionalOnMissingBean(names=...)} 跳过 starter 默认装配。
 */
@Configuration
public class LangChain4jHttpClientConfig {

    @Bean
    public HttpClientBuilder openAiStreamingChatModelHttpClientBuilder() {
        HttpClient.Builder jdkBuilder = HttpClient.newBuilder()
                // 强制 HTTP/1.1。原假设(已被笔记 8 反证):JDK 21 异步 HTTP/2 在
                // macOS 上有 UnresolvedAddressException 暗坑。当前未重新验证是否真有必要,
                // 见类级 javadoc 的"保留观察"说明
                .version(HttpClient.Version.HTTP_1_1)
                // 显式无代理,覆盖 JDK 默认 ProxySelector 在异步路径上的不确定行为。
                // 同样属于"防御性配置但根因未确定"系列
                .proxy(ProxySelector.of(null))
                .connectTimeout(Duration.ofSeconds(30));

        return JdkHttpClient.builder()
                .httpClientBuilder(jdkBuilder);
    }
}
