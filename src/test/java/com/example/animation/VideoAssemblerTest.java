package com.example.animation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoAssemblerTest {

    @Test
    void 拼接两个片段产出非空成片(@TempDir Path tmp) throws Exception {
        Path a = tmp.resolve("a.mp4");
        Path b = tmp.resolve("b.mp4");
        genClip(a, "red");
        genClip(b, "blue");

        Path out = tmp.resolve("out.mp4");
        VideoAssembler.concat(List.of(a, b), out);

        assertTrue(Files.exists(out), "应产出拼接文件");
        assertTrue(Files.size(out) > 0, "拼接文件不应为空");
    }

    /** 用 ffmpeg 生成 1 秒纯色测试片段(相同编码参数,保证 -c copy 拼接能过) */
    private static void genClip(Path dest, String color) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", "-f", "lavfi", "-i", "color=c=" + color + ":s=320x240:d=1",
                "-c:v", "mpeg4", "-pix_fmt", "yuv420p", dest.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        int exit = p.waitFor();
        if (exit != 0) {
            String err = new String(p.getInputStream().readAllBytes());
            throw new IllegalStateException("生成测试片段失败 exit=" + exit + ": " + err);
        }
    }
}
