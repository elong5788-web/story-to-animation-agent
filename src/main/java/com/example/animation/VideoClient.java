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

/**
 * 负责调用火山引擎 Ark 的 Seedance 视频生成(异步任务)。
 * 支持文生视频、图生视频(首帧)。
 */
public class VideoClient {

    static final String BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    static final String MODEL = "doubao-seedance-2-0-fast-260128";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** 文生视频 */
    public String submit(String prompt, int durationSeconds) throws Exception {
        String contentJson = "[{\"type\": \"text\", \"text\": \"%s\"}]".formatted(escape(prompt));
        return submitTask(contentJson, durationSeconds);
    }

    /** 图生视频:以图片为首帧,让它动起来 */
    public String submitImageToVideo(String imageUrl, String prompt, int durationSeconds) throws Exception {
        String contentJson = """
                [
                  {"type": "text", "text": "%s"},
                  {"type": "image_url", "image_url": {"url": "%s"}, "role": "first_frame"}
                ]
                """.formatted(escape(prompt), escape(imageUrl));
        return submitTask(contentJson, durationSeconds);
    }

    /** 首尾帧生视频:两张图定义起点和终点 */
    public String submitFirstLastFrame(String firstFrameUrl, String lastFrameUrl, String prompt, int durationSeconds) throws Exception {
        String contentJson = """
                [
                  {"type": "text", "text": "%s"},
                  {"type": "image_url", "image_url": {"url": "%s"}, "role": "first_frame"},
                  {"type": "image_url", "image_url": {"url": "%s"}, "role": "last_frame"}
                ]
                """.formatted(escape(prompt), escape(firstFrameUrl), escape(lastFrameUrl));
        return submitTask(contentJson, durationSeconds);
    }

    private String submitTask(String contentJson, int durationSeconds) throws Exception {
        String apiKey = Config.get("ARK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("请在 config.properties 里填 ARK_API_KEY");
        }
        String resolution = Config.get("RESOLUTION");
        if (resolution == null || resolution.isBlank()) resolution = "720p";

        String body = """
                {
                  "model": "%s",
                  "content": %s,
                  "duration": %d,
                  "resolution": "%s",
                  "generate_audio": false
                }
                """.formatted(MODEL, contentJson, durationSeconds, resolution);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/contents/generations/tasks"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
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
    public String waitForVideo(String taskId) throws Exception {
        String apiKey = Config.get("ARK_API_KEY");
        for (int i = 0; i < 60; i++) {   // 最多 60 次 × 5 秒 = 5 分钟
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/contents/generations/tasks/" + taskId))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = mapper.readTree(resp.body());
            String status = node.path("status").asText();
            System.out.println("   [进度] 第 " + (i + 1) + " 次查询: " + status);
            if ("succeeded".equals(status) || "success".equals(status)) {
                return extractVideoUrl(node);
            }
            if ("failed".equals(status) || "error".equals(status) || "expired".equals(status)) {
                throw new IllegalStateException("视频生成失败,响应:" + resp.body());
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

    /** 下载视频到本地 */
    public void download(String url, Path dest) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<byte[]> resp = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("下载失败 HTTP " + resp.statusCode());
        }
        Files.write(dest, resp.body());
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
