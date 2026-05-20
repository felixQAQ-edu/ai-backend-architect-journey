package com.felix.chatpipeline;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

class RawJdkHttpClientTest {

    @Test
    void rawJdkCanReachOpenAi() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/models"))
                .header("Authorization", "Bearer " + System.getenv("LLM_API_KEY"))
                .GET()
                .build();

        long t0 = System.currentTimeMillis();
        try {
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            long ms = System.currentTimeMillis() - t0;
            System.out.println("=== RAW JDK === status=" + resp.statusCode() + " in " + ms + "ms");
            System.out.println("=== RAW JDK === body[0..200]="
                    + resp.body().substring(0, Math.min(200, resp.body().length())));
        } catch (Throwable e) {
            long ms = System.currentTimeMillis() - t0;
            System.err.println("=== RAW JDK === failed in " + ms + "ms");
            e.printStackTrace();
            throw e;
        }
    }
}