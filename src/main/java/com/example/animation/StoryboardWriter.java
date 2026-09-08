package com.example.animation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 多镜头分镜产出:把小说拆解 + 多镜头分镜渲染成脚本,打印并保存。
 */
public class StoryboardWriter {

    public static void write(Console console, NovelBreakdown b, StoryBoard board, String stamp) throws Exception {
        String line = "-----------------------------------------------";
        StringBuilder sb = new StringBuilder();
        sb.append("【画风】").append(b.style()).append("\n");
        sb.append("【角色】").append(b.characters()).append("\n\n");
        sb.append("【世界观】").append(b.world().toText()).append("\n\n");
        sb.append("【多镜头分镜】\n");
        for (int i = 0; i < board.shots().size(); i++) {
            Shot s = board.shots().get(i);
            sb.append("镜头").append(i + 1).append(" ").append(s.time()).append(" ").append(s.framing()).append("\n");
            sb.append("  场景:").append(s.scene()).append("\n");
            sb.append("  动作:").append(s.action()).append("\n");
            sb.append("  运镜:").append(s.camera()).append(" | 情绪:").append(s.emotion()).append("\n");
        }

        console.println("\n" + line);
        console.println("【最终多镜头分镜脚本】");
        console.println(line);
        console.println(sb.toString());
        console.println(line);

        Path out = Path.of("output", "storyboard-" + stamp + ".txt");
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        console.println("已保存到: " + out.toAbsolutePath());
    }
}
