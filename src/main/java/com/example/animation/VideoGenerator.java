package com.example.animation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 视频生成:关键帧(用户提供 或 AI 文生图给你看)→ 图生视频。
 * 这里只生成一段「预览短片」,时长由 config.properties 的 DURATION 决定(默认 5 秒)。
 * 分镜设计里的时间轴(比如 0-12 秒)是一段更长的创意描述,和这个预览时长是两回事,别搞混。
 */
public class VideoGenerator {

    /** 生成一段预览短片:关键帧 → 图生视频。 */
    public static void generateShortVideo(Console console, ShotDesign d, String stamp) throws Exception {
        int duration = Config.getInt("DURATION", 5);
        String scene = d.keyframePrompt();
        String motion = d.motionPrompt();

        // 1. 关键帧:用户提供图,或 AI 文生图(生成后给你看,满意才继续)
        String keyframe = askForKeyframe(console, scene);
        if (keyframe == null) return;

        // 2. 图生视频
        console.println("\n生成视频(图生视频,约 1~3 分钟)...");
        VideoClient video = new VideoClient();
        String taskId = video.submitImageToVideo(keyframe, motion, duration);
        String url = video.waitForVideo(taskId, console);
        Path out = Path.of("output", "video-" + stamp + ".mp4");
        video.download(url, out);
        console.println("视频已生成: " + out.toAbsolutePath());
    }

    /** 关键帧来源:粘贴路径/网址,或序号选 materials/,或回车 AI 生成 */
    static String askForKeyframe(Console console, String scene) throws Exception {
        List<Path> materialsImages = listMaterialsImages();
        while (true) {
            if (!materialsImages.isEmpty()) {
                console.println("\nmaterials/ 里有图:");
                for (int i = 0; i < materialsImages.size(); i++) {
                    console.println("  " + (i + 1) + ". " + materialsImages.get(i).getFileName());
                }
            }
            console.println("关键帧来源:");
            console.println("  · 粘贴图片文件路径 或 网址(https://... 结尾是 .jpg/.png 的图片)");
            if (!materialsImages.isEmpty()) console.println("  · 输入序号,选 materials/ 里的图");
            console.println("  · 直接回车 → 让 AI 文生图");
            console.print("> ");
            String answer = console.readLine().trim();

            if (answer.isBlank()) {
                return aiKeyframeWithReview(console, scene);
            }
            if (answer.startsWith("http://") || answer.startsWith("https://")) {
                return answer;
            }
            if (!materialsImages.isEmpty()) {
                try {
                    int idx = Integer.parseInt(answer) - 1;
                    if (idx >= 0 && idx < materialsImages.size()) {
                        return ImageClient.toDataUrl(materialsImages.get(idx));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            Path p = Path.of(InputHandler.normalizePath(answer));
            if (Files.exists(p) && Files.isRegularFile(p)) {
                return ImageClient.toDataUrl(p);
            }
            console.println("  没找到这个路径或网址,再试一次\n");
        }
    }

    /** AI 文生图 + 用户确认,返回 dataURL(取消返回 null) */
    static String aiKeyframeWithReview(Console console, String scene) throws Exception {
        ImageClient image = new ImageClient();
        while (true) {
            console.println("\n① 文生图:生成关键帧(约 10~30 秒)...");
            String url = image.textToImage(scene);
            Path keyframe = Path.of("output", "keyframe-" + InputHandler.timestamp() + ".jpg");
            image.download(url, keyframe);
            console.println("   关键帧已生成: " + keyframe);
            console.println("   (可打开这个文件查看)");
            console.print("   满意吗?(y 满意 / r 重新生成 / n 取消): ");
            String answer = console.readLine().trim();
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) {
                return ImageClient.toDataUrl(keyframe);
            }
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no") || answer.equals("取消")) {
                return null;
            }
        }
    }

    static List<Path> listMaterialsImages() throws Exception {
        Path dir = Path.of("materials");
        List<Path> images = new ArrayList<>();
        if (Files.exists(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp");
                }).forEach(images::add);
            }
        }
        return images;
    }
}
