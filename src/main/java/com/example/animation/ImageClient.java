package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

/**
 * 负责调 Seedream(火山引擎 Ark)文生图:文字 → 图片。
 * 接口是同步的:直接返回图片 URL(不用像视频那样轮询任务)。
 */
public class ImageClient {

    static final String API_URL = "https://ark.cn-beijing.volces.com/api/v3/images/generations";
    static final String MODEL = "doubao-seedream-5-0-260128";   // Seedream 5.0

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** 文生图:返回图片 URL */
    public String textToImage(String prompt) throws Exception {
        String apiKey = Config.get("ARK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请在 config.properties 里填 ARK_API_KEY");
        }
        String size = Config.get("IMAGE_SIZE");
        if (size == null || size.isBlank()) size = "2K";

        String body = """
                {
                  "model": "%s",
                  "prompt": "%s",
                  "size": "%s",
                  "watermark": false
                }
                """.formatted(MODEL, escape(prompt), size);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Seedream 生成图片失败,HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode node = mapper.readTree(resp.body());
        JsonNode data = node.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new IllegalStateException("Seedream 没返回图片: " + resp.body());
        }
        String url = data.get(0).path("url").asText();
        if (url.isBlank()) {
            throw new IllegalStateException("Seedream 图片 URL 为空: " + resp.body());
        }
        return url;
    }

    /** 下载图片到本地 */
    public void download(String url, Path dest) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<byte[]> resp = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("下载图片失败 HTTP " + resp.statusCode());
        }
        Files.write(dest, resp.body());
    }

    /** 把本地图片转成 base64 data URL(给图生视频用,避免图片 URL 过期) */
    static String toDataUrl(Path imageFile) throws Exception {
        byte[] bytes = Files.readAllBytes(imageFile);
        String b64 = Base64.getEncoder().encodeToString(bytes);
        String name = imageFile.getFileName().toString().toLowerCase();
        String fmt = "jpeg";
        if (name.endsWith(".png")) fmt = "png";
        else if (name.endsWith(".webp")) fmt = "webp";
        return "data:image/" + fmt + ";base64," + b64;
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
