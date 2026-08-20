package com.example.animation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 负责用 ffmpeg 把多个视频片段按顺序拼接成一条成片。
 */
public class VideoAssembler {

    private final String ffmpegPath;

    public VideoAssembler(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    /** 把 videos 里的片段按顺序拼成一个 output */
    public void concat(List<Path> videos, Path output) throws Exception {
        // 1. 写一个"列表文件",告诉 ffmpeg 按什么顺序拼接
        Path listFile = Files.createTempFile("concat-list", ".txt");
        StringBuilder sb = new StringBuilder();
        for (Path v : videos) {
            // Windows 路径要转成正斜杠,ffmpeg 才认
            String p = v.toAbsolutePath().toString().replace('\\', '/');
            sb.append("file '").append(p).append("'\n");
        }
        Files.writeString(listFile, sb.toString());

        // 2. 调用 ffmpeg 拼接(-c copy 直接复制不重新编码,快)
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath,
                "-y",                    // 已存在则覆盖
                "-f", "concat",
                "-safe", "0",
                "-i", listFile.toString(),
                "-c", "copy",
                output.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String log = new String(process.getInputStream().readAllBytes());
        int exit = process.waitFor();

        Files.deleteIfExists(listFile);

        if (exit != 0) {
            throw new IllegalStateException("ffmpeg 拼接失败:\n" + log);
        }
    }
}
