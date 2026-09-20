package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 火山引擎 Ark 向量化(embedding)客户端。
 * 走多模态 embedding 端点(api/v3/embeddings/multimodal),模型 doubao-embedding-vision-251215。
 * 该端点不支持批量:一次只向量化一条文本,返回 data.embedding(2048 维)。
 * 批量由 Embedder 默认实现退化为逐条调用。
 */
public class EmbeddingClient implements Embedder {

    static final String API_URL = "https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal";
    static final String DEFAULT_MODEL = "doubao-embedding-vision-251215";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public float[] embed(String text) throws Exception {
        String apiKey = Config.get("ARK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请在 config.properties 里填 ARK_API_KEY");
        }
        String model = Config.get("EMBEDDING_MODEL");
        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
        }

        String body = """
                {"model": "%s", "input": [{"type": "text", "text": "%s"}], "encoding_format": "float"}
                """.formatted(model, TextUtil.jsonEscape(text));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Embedding 失败,HTTP " + resp.statusCode() + ": " + resp.body());
        }
        JsonNode emb = mapper.readTree(resp.body()).path("data").path("embedding");
        if (!emb.isArray() || emb.isEmpty()) {
            throw new IllegalStateException("Embedding 返回格式异常: " + resp.body());
        }
        float[] f = new float[emb.size()];
        for (int i = 0; i < emb.size(); i++) {
            f[i] = (float) emb.get(i).asDouble();
        }
        return f;
    }
}
