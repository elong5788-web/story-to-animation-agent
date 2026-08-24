package com.example.animation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 最终产出:完整分镜脚本 + 超详细提示词,打印并保存到文件。
 */
public class ScriptWriter {

    public static void write(Localization loc, WorldBuilding world, ShotDesign d, String stamp) throws Exception {
        String line = "-----------------------------------------------";
        String fullPrompt = d.style() + "," + d.subject() + "," + d.clothing() + "," + d.setting()
                + "," + world.toCoreText() + "," + d.camera() + "," + d.action() + "," + d.quality();

        System.out.println("\n" + line);
        System.out.println("【最终分镜脚本】");
        System.out.println(line);
        System.out.println("作品:" + loc.work() + " · " + loc.scene());
        System.out.println("角色:" + loc.characters());
        System.out.println("剧情:" + loc.plot());
        System.out.println("名场面:" + loc.iconicVisual());
        System.out.println("风格:" + loc.style());
        System.out.println("\n[世界观氛围]");
        System.out.println(world.toText());
        System.out.println("\n[镜头设计]");
        System.out.println("主体:" + d.subject());
        System.out.println("服装:" + d.clothing());
        System.out.println("场景:" + d.setting());
        System.out.println("风格:" + d.style());
        System.out.println("画质:" + d.quality());
        System.out.println("镜头:" + d.camera());
        System.out.println("叙事:" + d.narrative());
        System.out.println("动作:" + d.action());
        System.out.println("\n" + line);
        System.out.println("【完整提示词(可直接复制到 cineART/即梦/可灵等使用)】");
        System.out.println(line);
        System.out.println(fullPrompt);
        System.out.println(line);

        Path out = Path.of("output", "prompt-" + stamp + ".txt");
        StringBuilder sb = new StringBuilder();
        sb.append("作品:").append(loc.work()).append(" · ").append(loc.scene()).append("\n");
        sb.append("角色:").append(loc.characters()).append("\n");
        sb.append("剧情:").append(loc.plot()).append("\n");
        sb.append("名场面:").append(loc.iconicVisual()).append("\n\n");
        sb.append("[世界观氛围]\n").append(world.toText()).append("\n\n");
        sb.append("[镜头设计]\n");
        sb.append("主体:").append(d.subject()).append("\n");
        sb.append("服装:").append(d.clothing()).append("\n");
        sb.append("场景:").append(d.setting()).append("\n");
        sb.append("风格:").append(d.style()).append("\n");
        sb.append("画质:").append(d.quality()).append("\n");
        sb.append("镜头:").append(d.camera()).append("\n");
        sb.append("叙事:").append(d.narrative()).append("\n");
        sb.append("动作:").append(d.action()).append("\n\n");
        sb.append("[完整提示词]\n").append(fullPrompt).append("\n");
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("\n已保存到: " + out.toAbsolutePath());
    }
}
