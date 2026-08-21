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
 * 负责调用火山引擎 Ark 的 Seedance 视频生成(异步任务):
 * 提交任务 → 轮询进度 → 拿到视频地址 → 下载。
 */
public class VideoClient {

    // ===== 可能需要调整的配置,集中放这里 =====
    static final String BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";
    static final String MODEL = "doubao-seedance-2-0-fast-260128";   // ← 模型 ID(带版本号)
    // ================================================

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /** 提交一个文生视频任务,返回任务 id;durationSeconds 是该镜头时长 */
    public String submit(String prompt, int durationSeconds) throws Exception {
        String resolution = Config.get("RESOLUTION");
        if (resolution == null || resolution.isBlank()) {
            resolution = "720p";
        }
        String body = """
                {
                  "model": "%s",
                  "content": [{"type": "text", "text": "%s"}],
                  "parameters": {"resolution": "%s", "duration": %d}
                }
                """.formatted(MODEL, escape(prompt), resolution, durationSeconds);

        HttpResponse<String> resp = send("POST", BASE_URL + "/contents/generations/tasks", body);
        JsonNode node = mapper.readTree(resp.body());
        String taskId = node.path("id").asText();
        if (taskId.isBlank()) {
            throw new IllegalStateException("提交失败,响应:" + resp.body());
        }
        return taskId;
    }

    /** 轮询任务直到完成,返回视频下载地址 */
    public String waitForVideo(String taskId) throws Exception {
        for (int i = 0; i < 60; i++) {   // 最多等 60 次 × 5 秒 = 5 分钟
            HttpResponse<String> resp = send("GET", BASE_URL + "/contents/generations/tasks/" + taskId, null);
            JsonNode node = mapper.readTree(resp.body());
            String status = node.path("status").asText();
            System.out.println("   [进度] 第 " + (i + 1) + " 次查询: " + status);
            if ("succeeded".equals(status) || "success".equals(status)) {
                return extractVideoUrl(node);
            }
            if ("failed".equals(status) || "error".equals(status)) {
                throw new IllegalStateException("视频生成失败,响应:" + resp.body());
            }
            Thread.sleep(5000);
        }
        throw new IllegalStateException("等待超时");
    }

    /** 从任务结果里提取视频地址(兼容几种常见字段) */
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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<byte[]> resp = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("下载失败 HTTP " + resp.statusCode());
        }
        Files.write(dest, resp.body());
    }

    private HttpResponse<String> send(String method, String url, String body) throws Exception {
        String key = Config.get("ARK_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("请在 config.properties 里填 ARK_API_KEY(或设置环境变量)");
        }
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json");
        if ("POST".equals(method)) {
            b.POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            b.GET();
        }
        HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return resp;
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
