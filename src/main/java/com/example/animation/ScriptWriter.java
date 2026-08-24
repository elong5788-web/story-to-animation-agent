package com.example.animation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 最终产出:完整分镜脚本 + 结构化提示词,打印并保存到文件。
 */
public class ScriptWriter {

    public static void write(Localization loc, WorldBuilding world, ShotDesign d, String stamp) throws Exception {
        String line = "-----------------------------------------------";

        // 合成结构化完整提示词(可直接复制)
        StringBuilder prompt = new StringBuilder();
        prompt.append("【画风】").append(d.style()).append("\n");
        prompt.append("【主体】").append(d.subject()).append("\n");
        prompt.append("【服装】").append(d.clothing()).append("\n");
        prompt.append("【场景】").append(d.setting()).append("\n");
        prompt.append("【氛围】").append(world.toCoreText()).append("\n");
        prompt.append("【运镜】").append(d.camera()).append("\n");
        prompt.append("【情绪】").append(d.emotion()).append("\n");
        prompt.append("【动作】").append(d.action()).append("\n");
        prompt.append("【画质】").append(d.quality()).append("\n");
        prompt.append("【负面约束】").append(d.negative());
        String fullPrompt = prompt.toString();

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
        sb.append("[完整提示词]\n").append(fullPrompt).append("\n");
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        System.out.println("\n已保存到: " + out.toAbsolutePath());
    }
}
