package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 负责和大模型(DeepSeek)通信:把请求发过去,把回复文字拿回来。
 */
public class DeepSeekClient {

    static final String API_URL = "https://api.deepseek.com/chat/completions";
    static final String MODEL = "deepseek-chat";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** 把系统提示 + 用户消息发给 DeepSeek,返回它的回复文字 */
    public String chat(String systemPrompt, String userMessage) throws Exception {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请先设置环境变量 DEEPSEEK_API_KEY");
        }

        String body = """
                {
                  "model": "%s",
                  "messages": [
                    {"role": "system", "content": "%s"},
                    {"role": "user", "content": "%s"}
                  ],
                  "temperature": 0.7
                }
                """.formatted(MODEL, escape(systemPrompt), escape(userMessage));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("DeepSeek 请求失败,HTTP " + response.statusCode() + ": " + response.body());
        }

        // 从返回的大 JSON 里取出 choices[0].message.content 这段文字
        JsonNode root = mapper.readTree(response.body());
        return root.path("choices").get(0).path("message").path("content").asText();
    }

    /** 把字符串转成能安全放进 JSON 的文本 */
    static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
