package com.example.animation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 最终产出:把定位 + 世界观 + 分镜设计,渲染成一份专业 VIDEOPROMPT,打印并保存到文件。
 */
public class ScriptWriter {

    public static void write(Console console, Localization loc, WorldBuilding world, ShotDesign d, String stamp) throws Exception {
        String line = "-----------------------------------------------";

        // 渲染成专业 VIDEOPROMPT(对齐 AIGC 教程的 6 段式结构)
        StringBuilder full = new StringBuilder();
        full.append("【基础设定】\n");
        full.append("角色:").append(d.character()).append("\n");
        full.append("场景:").append(d.scene()).append("\n\n");
        full.append("【氛围与画质】\n");
        full.append("画风:").append(d.style()).append("\n");
        full.append("画质:").append(d.quality()).append("\n");
        full.append("氛围:").append(world.toText()).append("\n\n");
        full.append("【画面内容】\n");
        for (ShotDesign.Timeline t : d.timeline()) {
            full.append(t.time()).append(" ").append(t.framing()).append(": ").append(t.action())
                    .append("\n  [运镜]").append(t.camera())
                    .append("\n  [情绪]").append(t.emotion()).append("\n");
        }
        full.append("\n【声音】").append(d.sound()).append("\n");
        full.append("【限制】").append(d.negative());
        String fullPrompt = full.toString();

        console.println("\n" + line);
        console.println("【最终分镜脚本】");
        console.println(line);
        console.println("作品:" + loc.work() + " · " + loc.scene());
        console.println("角色:" + loc.characters());
        console.println("剧情:" + loc.plot());
        console.println("名场面:" + loc.iconicVisual());
        console.println("风格:" + loc.style());
        console.println("\n[世界观氛围]");
        console.println(world.toText());
        console.println("\n" + line);
        console.println("【完整提示词(可直接复制到 cineART/即梦/可灵等使用)】");
        console.println(line);
        console.println(fullPrompt);
        console.println(line);

        Path out = Path.of("output", "prompt-" + stamp + ".txt");
        StringBuilder sb = new StringBuilder();
        sb.append("作品:").append(loc.work()).append(" · ").append(loc.scene()).append("\n");
        sb.append("角色:").append(loc.characters()).append("\n");
        sb.append("剧情:").append(loc.plot()).append("\n");
        sb.append("名场面:").append(loc.iconicVisual()).append("\n\n");
        sb.append("[世界观氛围]\n").append(world.toText()).append("\n\n");
        sb.append("[完整提示词]\n").append(fullPrompt).append("\n");
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        console.println("\n已保存到: " + out.toAbsolutePath());
    }
}
