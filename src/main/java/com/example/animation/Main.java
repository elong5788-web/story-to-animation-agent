package com.example.animation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 入口:组装 agent(控制台 + 共享 DeepSeekClient + skill 流水线)并启动。
 * 输入 → 情节定位 → 世界观 → 分镜设计 → 产出 → 可选生成预览视频。
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Console console = new SystemConsole();
        DeepSeekClient ds = new DeepSeekClient();
        Files.createDirectories(Path.of("output"));
        if (args.length > 0 && "--resume".equals(args[0])) {
            if (args.length != 2 || args[1].isBlank()) {
                throw new IllegalArgumentException("用法: ./mvnw compile exec:java -Dexec.args=\"--resume 任务编号\"");
            }
            resumeNovelVideo(console, args[1]);
            return;
        }
        String stamp = InputHandler.timestamp();

        // 1. 输入
        String fromFile = InputHandler.readStory(args);
        console.println("当前 story.txt: " + fromFile);
        console.println("输入画面(回车用上面的;可粘贴多行文字,或输入 .txt 文件路径;空行结束): ");
        String typed = console.readRest();
        String input = InputHandler.resolveInput(console, typed, fromFile);
        console.println("你的输入: " + input);

        // 2. 选模式:显式确认,别再用字数猜(一句话写长一点就会误入小说模式)
        Context ctx = new Context(input);
        Retriever retriever = new Retriever();
        boolean isNovel = chooseNovelMode(console, input);
        if (isNovel) {
            // 读小说模式:小说 → 拆解 → 多镜头分镜
            Agent agent = new Agent(List.of(
                    new NovelParser(ds),
                    new StoryboardDesigner(ds, retriever)));
            agent.run(ctx, console);
            if (ctx.cancelled()) return;
            StoryboardWriter.write(console, ctx.novelBreakdown(), ctx.storyBoard(), stamp);
            NovelVideoJobStore.save(stamp, input, ctx.novelBreakdown(), ctx.storyBoard());
            console.println("小说视频任务快照: " + NovelVideoJobStore.path(stamp).toAbsolutePath());

            // 第 2 步:给每个镜头生成关键帧图 + 描述
            console.print("\n要不要给每个镜头生成关键帧图 + 描述?(y=生成,回车跳过): ");
            String genImg = console.readLine();
            if (genImg != null && (genImg.trim().equalsIgnoreCase("y") || genImg.trim().equalsIgnoreCase("yes"))) {
                ShotImageGenerator.generate(console, ctx.novelBreakdown(), ctx.storyBoard(), stamp);
            }
        } else {
            // 短片模式:一句话 → 定位 → 世界观 → 分镜
            Agent agent = new Agent(List.of(
                    new Localizer(ds),
                    new WorldBuilder(ds),
                    new ShotDesigner(ds, retriever)));
            agent.run(ctx, console);
            if (ctx.cancelled()) return;
            ScriptWriter.write(console, ctx.loc(), ctx.world(), ctx.design(), stamp);
        }

        // 4. 可选:生成视频(按模式分发)
        console.print("\n要不要顺便生成视频?(y=生成,回车跳过): ");
        String gen = console.readLine();
        if (gen != null && (gen.trim().equalsIgnoreCase("y") || gen.trim().equalsIgnoreCase("yes"))) {
            if (isNovel) {
                NovelVideoGenerator.generate(console, ctx.novelBreakdown(), ctx.storyBoard(), stamp);
            } else {
                VideoGenerator.generateShortVideo(console, ctx.design(), stamp);
            }
        }
    }

    private static void resumeNovelVideo(Console console, String stamp) throws Exception {
        NovelVideoJob job = NovelVideoJobStore.load(stamp);
        console.println("从任务 " + stamp + " 继续,共 " + job.storyboard().shots().size() + " 个镜头。");
        NovelVideoGenerator.generate(console, job.breakdown(), job.storyboard(), stamp);
    }

    /** 选模式:按字数给默认建议,用户回车确认或显式指定 1/2。 */
    private static boolean chooseNovelMode(Console console, String input) {
        boolean guess = input.length() > 100;
        console.println("\n选择运行模式:");
        console.println("  1. 短片模式 — 一句话 → 单个镜头提示词 + 可选预览视频");
        console.println("  2. 读小说模式 — 小说 → 多镜头分镜 + 每镜配图");
        console.print("回车 = 自动(" + (guess ? "读小说" : "短片") + "),或输入 1/2: ");
        String choice = console.readLine();
        if (choice == null) return guess;
        choice = choice.trim();
        if (choice.equals("1")) return false;
        if (choice.equals("2")) return true;
        return guess;
    }
}
