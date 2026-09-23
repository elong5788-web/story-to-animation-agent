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
        return chat(systemPrompt, userMessage, false);
    }

    /** 结构化输出:要求 DeepSeek 只返回合法 JSON(定位/世界观/分镜用) */
    public String chatJson(String systemPrompt, String userMessage) throws Exception {
        return chat(systemPrompt, userMessage, true);
    }

    private String chat(String systemPrompt, String userMessage, boolean jsonMode) throws Exception {
        String apiKey = Config.get("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请在 config.properties 里填 DEEPSEEK_API_KEY(或设置环境变量)");
        }

        // json 模式:要求返回纯 JSON。DeepSeek 要求 prompt 里含 "json" 字样(我们的提示词都满足)
        var bodyNode = mapper.createObjectNode()
                .put("model", MODEL)
                .put("temperature", jsonMode ? Config.getDouble("JSON_TEMPERATURE", 0.65, 0.0, 2.0) : 1.0);
        var messages = bodyNode.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userMessage);
        if (jsonMode) bodyNode.putObject("response_format").put("type", "json_object");
        String body = mapper.writeValueAsString(bodyNode);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("DeepSeek 请求失败,HTTP " + response.statusCode() + ": " + response.body());
        }

        // 从返回的大 JSON 里取出 choices[0].message.content 这段文字
        JsonNode root = mapper.readTree(response.body());
        String content = root.path("choices").get(0).path("message").path("content").asText();
        // json 模式防御性剥掉模型偶尔加的 ```json ... ``` 外壳
        return jsonMode ? TextUtil.stripCodeFence(content) : content;
    }
}
