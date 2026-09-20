package com.example.animation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 用 ffmpeg 把多个镜头片段拼接成片(concat demuxer,-c copy 无重编码)。
 * 要求各片段编码参数一致(同一 Seedance 配置生成的片段满足)。
 */
public class VideoAssembler {

    public static Path concat(List<Path> clips, Path output) throws Exception {
        if (clips.isEmpty()) {
            throw new IllegalStateException("没有可拼接的片段");
        }
        // 写 concat 列表文件(绝对路径,统一正斜杠)
        Path listFile = Files.createTempFile("concat-", ".txt");
        StringBuilder sb = new StringBuilder();
        for (Path c : clips) {
            sb.append("file '").append(c.toAbsolutePath().toString().replace('\\', '/')).append("'\n");
        }
        Files.writeString(listFile, sb.toString(), StandardCharsets.UTF_8);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", listFile.toString(),
                    "-c", "copy", output.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exit = p.waitFor();
            if (exit != 0) {
                String err = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("ffmpeg 拼接失败,exit=" + exit + ": " + err);
            }
        } finally {
            Files.deleteIfExists(listFile);
        }
        return output;
    }
}
