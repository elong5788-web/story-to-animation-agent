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
 * 负责调 Seedream(火山引擎 Ark)图片生成:文生图 + 图生图。
 * 接口是同步的:直接返回图片 URL。
 */
public class ImageClient {

    static final String API_URL = "https://ark.cn-beijing.volces.com/api/v3/images/generations";
    static final String MODEL = "doubao-seedream-5-0-260128";   // Seedream 5.0

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** 文生图:文字 → 图片 URL */
    public String textToImage(String prompt) throws Exception {
        return generate(imageRequest(prompt, null));
    }

    /** 图生图:以参考图为底,按提示词修改(用于生成连贯的尾帧) */
    public String imageToImage(String prompt, String referenceDataUrl) throws Exception {
        return generate(imageRequest(prompt, referenceDataUrl));
    }

    private String imageRequest(String prompt, String referenceDataUrl) throws Exception {
        var body = mapper.createObjectNode().put("model", MODEL).put("prompt", prompt)
                .put("size", imageSize()).put("watermark", false);
        if (referenceDataUrl != null) body.putArray("image").add(referenceDataUrl);
        return mapper.writeValueAsString(body);
    }

    private String imageSize() {
        String size = Config.get("IMAGE_SIZE");
        return (size == null || size.isBlank()) ? "2K" : size;
    }

    /** 提交并解析出图片 URL */
    private String generate(String body) throws Exception {
        String apiKey = Config.get("ARK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请在 config.properties 里填 ARK_API_KEY");
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(describeImageFailure(resp.body()));
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

    /** 把图片生成失败的错误码转成人类能看懂、能行动的提示 */
    private String describeImageFailure(String body) {
        try {
            JsonNode error = mapper.readTree(body).path("error");
            String code = error.path("code").asText("");
            String message = error.path("message").asText("");
            String combined = code + " " + message;
            if (combined.toLowerCase().contains("sensitive") || combined.contains("敏感")) {
                return "图片被内容安全审核拦截(输入文字疑似敏感,如「赤裸」「裸体」等词)。请调整措辞再试。\n原始信息: " + message;
            }
            if (combined.toLowerCase().contains("copyright") || combined.contains("版权")) {
                return "图片被版权审核拦截(内容疑似涉及版权角色)。请换一个原创角色再试。\n原始信息: " + message;
            }
            return "Seedream 生成图片失败(" + (code.isBlank() ? "未知原因" : code) + "): " + message;
        } catch (Exception e) {
            return "Seedream 生成图片失败: " + body;
        }
    }

    /** 下载图片到本地 */
    public void download(String url, Path dest) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMinutes(2)).GET().build();
        HttpResponse<byte[]> resp = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("下载图片失败 HTTP " + resp.statusCode());
        }
        Files.write(dest, resp.body());
    }

    /** 本地图片转 base64 data URL */
    static String toDataUrl(Path imageFile) throws Exception {
        byte[] bytes = Files.readAllBytes(imageFile);
        String b64 = Base64.getEncoder().encodeToString(bytes);
        String name = imageFile.getFileName().toString().toLowerCase();
        String fmt = "jpeg";
        if (name.endsWith(".png")) fmt = "png";
        else if (name.endsWith(".webp")) fmt = "webp";
        return "data:image/" + fmt + ";base64," + b64;
    }

}
