package com.example.animation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * 主程序:纯流程编排,不掺和具体逻辑。
 * 输入 → 情节定位 → 世界观 → 8 维度 → 产出 → 可选生成视频。
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        Files.createDirectories(Path.of("output"));
        String stamp = InputHandler.timestamp();

        // 1. 输入
        String fromFile = InputHandler.readStory(args);
        System.out.println("当前 story.txt: " + fromFile);
        System.out.println("输入画面(回车用上面的;可粘贴多行文字,或输入 .txt 文件路径;空行结束): ");
        String typed = InputHandler.readRest(sc);
        String input = InputHandler.resolveInput(typed, fromFile);
        System.out.println("你的输入: " + input);

        // 2. 情节定位
        Localization loc = Localizer.run(sc, input);
        if (loc == null) return;
        String context = input + "\n\n[定位信息]\n" + loc.toText();

        // 3. 世界观
        WorldBuilding world = WorldBuilder.run(sc, context);
        if (world == null) return;

        // 4. 8 个画面/动作维度
        ShotDesign design = ShotDesigner.run(sc, context, world);
        if (design == null) return;

        // 5. 产出:分镜脚本 + 提示词
        ScriptWriter.write(loc, world, design, stamp);

        // 6. 可选:生成视频
        System.out.print("\n要不要顺便生成视频/图片?(y=生成,回车跳过): ");
        String gen = sc.hasNextLine() ? sc.nextLine().trim() : "";
        if (gen.equalsIgnoreCase("y") || gen.equalsIgnoreCase("yes")) {
            VideoGenerator.generateShortVideo(sc, design, stamp);
        }
    }
}
