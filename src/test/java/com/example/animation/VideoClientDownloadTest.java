package com.example.animation;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VideoClientDownloadTest {

    @Test
    void 下载遇到服务端临时错误会重试并原子保存(@TempDir Path tempDir) throws Exception {
        byte[] video = "video-bytes".getBytes(StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/video", exchange -> {
            int attempt = requests.incrementAndGet();
            byte[] response = attempt == 1 ? "temporary".getBytes(StandardCharsets.UTF_8) : video;
            exchange.sendResponseHeaders(attempt == 1 ? 503 : 200, response.length);
            try (var out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        server.start();
        try {
            Path destination = tempDir.resolve("clip.mp4");
            new VideoClient().download("http://127.0.0.1:" + server.getAddress().getPort() + "/video", destination);

            assertArrayEquals(video, Files.readAllBytes(destination));
            assertEquals(2, requests.get());
        } finally {
            server.stop(0);
        }
    }
}
