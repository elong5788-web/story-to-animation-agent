package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * 负责调用火山引擎 Ark 的 Seedance 视频生成(异步任务)。
 * 支持文生视频、图生视频(首帧)、首尾帧、参考图(锁定角色一致性)。
 */
public class VideoClient {

    static final String BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    /** 文生视频 / 首帧 / 首尾帧 用的通用模型 */
    static final String MODEL = "doubao-seedance-2-0-fast-260128";
    /** 参考图(锁定角色一致性)专用模型 */
    static final String REFERENCE_MODEL = "doubao-seedance-1-0-lite-i2v-250428";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** 文生视频 */
    public String submit(String prompt, int durationSeconds) throws Exception {
        return submitTask(MODEL, textContent(prompt), durationSeconds);
    }

    /** 图生视频:以图片为首帧,让它动起来 */
    public String submitImageToVideo(String imageUrl, String prompt, int durationSeconds) throws Exception {
        return submitTask(MODEL, textContent(prompt).add(imageItem(imageUrl, "first_frame")), durationSeconds);
    }

    /** 首尾帧生视频:两张图定义起点和终点 */
    public String submitFirstLastFrame(String firstFrameUrl, String lastFrameUrl, String prompt, int durationSeconds) throws Exception {
        return submitTask(MODEL, textContent(prompt).add(imageItem(firstFrameUrl, "first_frame"))
                .add(imageItem(lastFrameUrl, "last_frame")), durationSeconds);
    }

    /** 参考图 + 首帧生视频:reference_image 锁定角色贯穿全片,first_frame 定义本镜起点 */
    public String submitReferenceToVideo(String referenceDataUrl, String firstFrameDataUrl, String prompt, int durationSeconds) throws Exception {
        return submitTask(REFERENCE_MODEL, textContent(prompt).add(imageItem(referenceDataUrl, "reference_image"))
                .add(imageItem(firstFrameDataUrl, "first_frame")), durationSeconds);
    }

    private com.fasterxml.jackson.databind.node.ArrayNode textContent(String prompt) {
        var content = mapper.createArrayNode();
        content.addObject().put("type", "text").put("text", prompt);
        return content;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode imageItem(String url, String role) {
        var item = mapper.createObjectNode().put("type", "image_url").put("role", role);
        item.putObject("image_url").put("url", url);
        return item;
    }

    private String submitTask(String model, com.fasterxml.jackson.databind.node.ArrayNode content, int durationSeconds) throws Exception {
        if (durationSeconds < 1) throw new IllegalArgumentException("DURATION 必须为正整数");
        String apiKey = Config.get("ARK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请在 config.properties 里填 ARK_API_KEY");
        }
        String resolution = Config.get("RESOLUTION");
        if (resolution == null || resolution.isBlank()) {
            resolution = "720p";
        }

        var bodyNode = mapper.createObjectNode().put("model", model);
        bodyNode.set("content", content);
        bodyNode.put("duration", durationSeconds).put("resolution", resolution).put("generate_audio", false);
        String body = mapper.writeValueAsString(bodyNode);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/contents/generations/tasks"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("视频任务提交失败,HTTP " + resp.statusCode() + ": " + resp.body());
        }
        JsonNode node = mapper.readTree(resp.body());
        String taskId = node.path("id").asText();
        if (taskId.isBlank()) {
            throw new IllegalStateException("提交失败,响应:" + resp.body());
        }
        return taskId;
    }

    /** 轮询任务直到完成,返回视频下载地址 */
    public String waitForVideo(String taskId, Console console) throws Exception {
        String apiKey = Config.get("ARK_API_KEY");
        for (int i = 0; i < 60; i++) {   // 最多 60 次 × 5 秒 = 5 分钟
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/contents/generations/tasks/" + taskId))
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                if (resp.statusCode() == 429 || resp.statusCode() >= 500) {
                    Thread.sleep(Math.min(15000, 2000L * (i + 1)));
                    continue;
                }
                throw new IllegalStateException("查询视频任务失败,HTTP " + resp.statusCode() + ": " + resp.body());
            }
            JsonNode node = mapper.readTree(resp.body());
            String status = node.path("status").asText();
            console.println("   [进度] 第 " + (i + 1) + " 次查询: " + status);
            if ("succeeded".equals(status) || "success".equals(status)) {
                return extractVideoUrl(node);
            }
            if ("failed".equals(status) || "error".equals(status) || "expired".equals(status)) {
                throw new IllegalStateException(describeVideoFailure(node));
            }
            Thread.sleep(5000);
        }
        throw new IllegalStateException("等待超时");
    }

    private String extractVideoUrl(JsonNode node) {
        String[] paths = {"content.video_url", "content.video_urls", "output.video_url", "data.video_url"};
        for (String p : paths) {
            JsonNode n = node;
            for (String part : p.split("\\.")) {
                n = n.path(part);
            }
            if (n.isArray() && !n.isEmpty()) {
                return n.get(0).asText();
            }
            if (n.isTextual() && !n.asText().isBlank()) {
                return n.asText();
            }
        }
        throw new IllegalStateException("没找到视频地址,完整响应:" + node.toString());
    }

    /** 把失败任务的错误码转成人类能看懂、能行动的提示 */
    private String describeVideoFailure(JsonNode node) {
        JsonNode error = node.path("error");
        String code = error.path("code").asText("");
        String message = error.path("message").asText("");
        String combined = code + " " + message;
        if (combined.toLowerCase().contains("copyright") || combined.contains("PolicyViolation") || combined.contains("版权")) {
            return "视频被版权审核拦截(内容疑似涉及版权角色/作品,如皮卡丘等 IP)。请换一个原创角色或题材再试。\n原始信息: " + message;
        }
        if (combined.toLowerCase().contains("sensitive") || combined.contains("敏感")) {
            return "视频被内容安全审核拦截(内容疑似敏感)。请调整措辞再试。\n原始信息: " + message;
        }
        return "视频生成失败(" + (code.isBlank() ? "未知原因" : code) + "): " + message;
    }

    /** 下载视频到本地 */
    public void download(String url, Path dest) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofMinutes(5)).GET().build();
        HttpResponse<byte[]> resp = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("下载失败 HTTP " + resp.statusCode());
        }
        Path parent = dest.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, dest.getFileName().toString(), ".part");
        try {
            Files.write(temp, resp.body());
            try {
                Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
