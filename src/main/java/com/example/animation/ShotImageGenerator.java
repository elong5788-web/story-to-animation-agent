package com.example.animation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 多镜头配图:给每个镜头生成一张关键帧图 + 一句描述,并保存配图清单。
 * 关键帧 prompt = 画风 + 角色卡 + 场景 + 景别 + 动作,保证全片角色/风格统一。
 */
public class ShotImageGenerator {

    public static void generate(Console console, NovelBreakdown b, StoryBoard board, String stamp) throws Exception {
        ImageClient image = new ImageClient();
        StringBuilder manifest = new StringBuilder();
        manifest.append("【画风】").append(b.style()).append("\n");
        manifest.append("【角色】").append(b.characters()).append("\n\n");

        String reference = null;   // 前一镜的关键帧 dataURL,用来锁住角色/场景的一致性
        for (int i = 0; i < board.shots().size(); i++) {
            Shot s = board.shots().get(i);
            String quality = s.quality().isBlank() ? "4K,电影级画质" : s.quality();
            String prompt = b.style() + "," + b.characters() + "," + s.scene() + "," + s.framing() + ","
                    + s.action() + "," + quality + ",无水印";

            String url;
            if (reference == null) {
                // 第一镜:文生图,定下全片角色/场景的视觉基准
                console.println("\n[" + (i + 1) + "/" + board.shots().size() + "] 生成镜头" + (i + 1) + " 关键帧(文生图,约 10~30 秒)...");
                url = image.textToImage(prompt);
            } else {
                // 后续镜:以前一镜为参考图做图生图,保证同一角色、同一风格贯穿全片
                console.println("\n[" + (i + 1) + "/" + board.shots().size() + "] 生成镜头" + (i + 1) + " 关键帧(图生图,以镜头" + i + "为参考,约 10~30 秒)...");
                url = image.imageToImage(prompt, reference);
            }
            Path out = Path.of("output", "shot-" + stamp + "-" + (i + 1) + ".jpg");
            image.download(url, out);
            reference = ImageClient.toDataUrl(out);

            String desc = "用途:镜头" + (i + 1) + "关键帧 | 画面:" + s.framing() + " " + s.scene() + " " + s.action()
                    + " | 运镜:" + s.camera() + " | 情绪:" + s.emotion();
            console.println("  图: " + out.toAbsolutePath());
            console.println("  " + desc);

            manifest.append("镜头").append(i + 1).append(" [").append(s.time()).append("] ").append(s.framing()).append("\n");
            manifest.append("  图: ").append(out.getFileName()).append("\n");
            manifest.append("  描述: ").append(desc).append("\n\n");
        }

        Path manifestOut = Path.of("output", "storyboard-images-" + stamp + ".txt");
        Files.writeString(manifestOut, manifest.toString(), StandardCharsets.UTF_8);
        console.println("\n配图清单已保存: " + manifestOut.toAbsolutePath());
    }
}
