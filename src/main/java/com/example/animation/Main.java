package com.example.animation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 主程序:输入一句话 → 选模式(短片/长片)→ DeepSeek 修饰 → 用户审查/修改 → Seedance 生成视频。
 *
 * 核心:agent 打包了"问 AI 怎么写提示词"+"把提示词喂给视频模型"的完整流程。
 */
public class Main {

    static final int DURATION = 5;
    static final String FFMPEG = "C:/Users/elong258/ffmpeg/bin/ffmpeg.exe";

    /** DeepSeek 角色:把一句话修饰成视频画面描述 */
    static final String EXPANDER_PROMPT = "你是一个 AI 视频创作助手。"
            + "请把用户的一句话修饰成一段具体、可视化的视频画面描述(80 字左右),"
            + "包含场景、氛围、光线、细节,便于视频生成模型理解。"
            + "只输出这一段描述,不要任何解释或多余内容。";

    public static void main(String[] args) throws Exception {
        String input = readStory(args);
        Scanner sc = new Scanner(System.in);

        System.out.println("你的输入: " + input);

        // 1. 选模式
        String mode;
        while (true) {
            System.out.print("\n选模式: 1=短片(一个视频)  2=长片(多镜头拼成片)  > ");
            mode = sc.hasNextLine() ? sc.nextLine().trim() : "1";
            if (mode.equals("1") || mode.equals("2")) break;
            System.out.println("请输入 1 或 2");
        }

        if (mode.equals("2")) {
            // ===== 长片模式 =====
            System.out.println("\n(提醒:长片目前各镜头间可能不连贯,这是 AI 视频的一致性难题)");
            StoryboardAgent agent = new StoryboardAgent();
            List<Shot> shots = agent.plan(input);
            shots = reviewShotsLoop(sc, agent, input, shots);
            if (shots == null) return;
            generateAndAssemble(shots);
        } else {
            // ===== 短片模式 =====
            DeepSeekClient ds = new DeepSeekClient();
            String description = ds.chat(EXPANDER_PROMPT, input);
            description = reviewPromptLoop(sc, ds, input, description);
            if (description == null) return;
            generateOneVideo(description);
        }
    }

    /** 短片:让用户审查一段画面描述,可反复修改;满意返回描述,取消返回 null */
    static String reviewPromptLoop(Scanner sc, DeepSeekClient ds, String input, String description) throws Exception {
        while (true) {
            System.out.println("\nDeepSeek 修饰后的画面描述:");
            System.out.println("  " + description);
            System.out.println("  · 输入 y → 满意,生成视频");
            System.out.println("  · 输入 n → 取消");
            System.out.println("  · 输入其他 → 当作修改意见,重新修饰");
            System.out.print("> ");
            String answer = sc.hasNextLine() ? sc.nextLine().trim() : "";
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) return description;
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no") || answer.equals("取消")) {
                System.out.println("已取消。");
                return null;
            }
            String userMsg = input + "\n\n(上一次修饰结果:[" + description + "],用户意见:" + answer + ",请重新修饰。)";
            description = ds.chat(EXPANDER_PROMPT, userMsg);
        }
    }

    /** 长片:让用户审查分镜,可反复修改;满意返回镜头列表,取消返回 null */
    static List<Shot> reviewShotsLoop(Scanner sc, StoryboardAgent agent, String input, List<Shot> shots) throws Exception {
        while (true) {
            printShots(shots);
            System.out.println("  · 输入 y → 满意,生成视频");
            System.out.println("  · 输入 n → 取消");
            System.out.println("  · 输入其他 → 当作修改意见,重新拆镜");
            System.out.print("> ");
            String answer = sc.hasNextLine() ? sc.nextLine().trim() : "";
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) return shots;
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no") || answer.equals("取消")) {
                System.out.println("已取消。");
                return null;
            }
            System.out.println("带着意见重新拆镜...");
            shots = agent.plan(input, answer);
        }
    }

    /** 短片:生成一个视频 */
    static void generateOneVideo(String description) throws Exception {
        System.out.println("\n开始生成视频(约 1~3 分钟)...");
        VideoClient video = new VideoClient();
        String taskId = video.submit(description, DURATION);
        String url = video.waitForVideo(taskId);
        Path out = Path.of("output.mp4");
        video.download(url, out);
        System.out.println("视频已生成: " + out.toAbsolutePath());
    }

    /** 长片:逐镜头生成 + 拼接 */
    static void generateAndAssemble(List<Shot> shots) throws Exception {
        VideoClient video = new VideoClient();
        List<Path> clips = new ArrayList<>();
        for (Shot s : shots) {
            System.out.println("\n【生成镜头 " + s.shot() + "/" + shots.size() + "】"
                    + s.shotType() + ",时长 " + s.duration() + " 秒");
            int duration = s.duration() >= 8 ? 10 : 5;
            String prompt = s.description() + "," + s.action();
            String taskId = video.submit(prompt, duration);
            String url = video.waitForVideo(taskId);
            Path out = Path.of("shot-" + s.shot() + ".mp4");
            video.download(url, out);
            clips.add(out);
            System.out.println("   镜头 " + s.shot() + " 完成 → " + out);
        }
        System.out.println("\n拼接成片...");
        VideoAssembler assembler = new VideoAssembler(FFMPEG);
        Path finalVideo = Path.of("final.mp4");
        assembler.concat(clips, finalVideo);
        System.out.println("成片已生成: " + finalVideo.toAbsolutePath());
    }

    static void printShots(List<Shot> shots) {
        System.out.println("\n=== 当前分镜 ===");
        for (Shot s : shots) {
            System.out.printf("镜头 %d(%s,时长 %d 秒)\n  画面: %s\n  动作: %s\n",
                    s.shot(), s.shotType(), s.duration(), s.description(), s.action());
        }
    }

    static String readStory(String[] args) throws Exception {
        Path storyFile = Path.of("story.txt");
        if (Files.exists(storyFile)) {
            return Files.readString(storyFile, StandardCharsets.UTF_8).trim();
        }
        if (args.length > 0) {
            return String.join(" ", args);
        }
        return "一个女孩在雨天撑伞走过街道";
    }
}
