package com.example.animation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
        Files.writeString(listFile, sb.toString());
        Path logFile = Files.createTempFile("ffmpeg-", ".log");
        Path absoluteOutput = output.toAbsolutePath();
        Path outputParent = absoluteOutput.getParent();
        Files.createDirectories(outputParent);
        Path tempOutput = Files.createTempFile(outputParent, "assembling-", ".mp4");

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", listFile.toString(),
                    "-c", "copy", tempOutput.toString());
            pb.redirectErrorStream(true).redirectOutput(logFile.toFile());
            Process p = pb.start();
            if (!p.waitFor(10, TimeUnit.MINUTES)) {
                p.destroyForcibly();
                throw new IllegalStateException("ffmpeg 拼接超时(10 分钟)");
            }
            int exit = p.exitValue();
            if (exit != 0) {
                String err = Files.readString(logFile);
                throw new IllegalStateException("ffmpeg 拼接失败,exit=" + exit + ": " + err);
            }
            try {
                Files.move(tempOutput, absoluteOutput, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tempOutput, absoluteOutput, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(listFile);
            Files.deleteIfExists(logFile);
            Files.deleteIfExists(tempOutput);
        }
        return absoluteOutput;
    }
}
