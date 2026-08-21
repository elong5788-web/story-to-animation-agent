package com.example.animation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 主程序:输入一句话 → 选模式(短片/长片)→ DeepSeek 修饰 → 用户审查/修改 → Seedance 生成视频。
 * 所有生成的视频统一放在 output/ 文件夹里,带时间戳不覆盖。
 */
public class Main {

    static final String FFMPEG = "C:/Users/elong258/ffmpeg/bin/ffmpeg.exe";
    static final String OUTPUT_DIR = "output";

    /** DeepSeek 角色:把一句话修饰成视频画面描述 */
    static final String EXPANDER_PROMPT = "你是一个 AI 视频创作助手。"
            + "请把用户的一句话修饰成一段具体、可视化的视频画面描述(80 字左右),"
            + "包含场景、氛围、光线、细节,便于视频生成模型理解。"
            + "只输出这一段描述,不要任何解释或多余内容。";

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        Files.createDirectories(Path.of(OUTPUT_DIR));   // 确保输出文件夹存在
        String stamp = timestamp();                      // 本次运行的时间戳

        // 1. 输入画面描述(可直接输入,回车则用 story.txt 里的)
        String fromFile = readStory(args);
        System.out.println("当前 story.txt: " + fromFile);
        System.out.print("输入你想生成的画面(直接回车用上面的): ");
        String typed = sc.hasNextLine() ? sc.nextLine().trim() : "";
        String input = typed.isBlank() ? fromFile : typed;
        if (!typed.isBlank()) {
            Files.writeString(Path.of("story.txt"), typed, StandardCharsets.UTF_8);
        }
        System.out.println("你的输入: " + input);

        // 2. 选模式
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
            generateAndAssemble(shots, stamp);
        } else {
            // ===== 短片模式 =====
            DeepSeekClient ds = new DeepSeekClient();
            String description = ds.chat(EXPANDER_PROMPT, input);
            description = reviewPromptLoop(sc, ds, input, description);
            if (description == null) return;
            generateShortVideo(sc, description, stamp);
        }
    }

    /** 短片:让用户审查一段画面描述,可反复修改;满意返回描述,取消返回 null */
    static String reviewPromptLoop(Scanner sc, DeepSeekClient ds, String input, String description) throws Exception {
        while (true) {
            System.out.println("\nDeepSeek 修饰后的画面描述:");
            System.out.println("  " + description);
            System.out.println("  · 输入 y → 满意,生成视频");
            System.out.println("  · 输入 n → 取消");
            System.out.println("  · 输入 r → 换一个不同的版本");
            System.out.println("  · 输入其他 → 当作修改意见,重新修饰");
            System.out.print("> ");
            String answer = sc.hasNextLine() ? sc.nextLine().trim() : "";
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) return description;
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no") || answer.equals("取消")) {
                System.out.println("已取消。");
                return null;
            }
            if (answer.equalsIgnoreCase("r") || answer.equals("换一个")) {
                description = ds.chat(EXPANDER_PROMPT, input + "\n\n(请给一个和上次完全不同的新版本)");
                continue;
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

    /** 短片:关键帧(用户提供或 AI 生成)→ 图生视频 */
    static void generateShortVideo(Scanner sc, String description, String stamp) throws Exception {
        int duration = Config.getInt("DURATION", 5);

        // 1. 确定关键帧:优先用户提供的图,否则 AI 文生图(带确认)
        Path keyframe = askForUserImage(sc);
        if (keyframe == null) {
            keyframe = generateKeyframeWithReview(sc, description);
            if (keyframe == null) return;   // 用户取消
        } else {
            System.out.println("\n使用你的参考图: " + keyframe);
        }

        // 2. 图生视频:文字是"动作描述",告诉视频模型怎么动
        System.out.println("\n② 图生视频:让关键帧动起来(约 1~3 分钟)...");
        String dataUrl = ImageClient.toDataUrl(keyframe);
        VideoClient video = new VideoClient();
        String taskId = video.submitImageToVideo(dataUrl, description, duration);
        String url = video.waitForVideo(taskId);
        Path out = Path.of(OUTPUT_DIR, "video-" + stamp + ".mp4");
        video.download(url, out);
        System.out.println("视频已生成: " + out.toAbsolutePath());
    }

    /** 列出 materials/ 里的图片让用户选;回车返回 null(让 AI 生成) */
    static Path askForUserImage(Scanner sc) throws Exception {
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
        if (images.isEmpty()) {
            return null;
        }
        System.out.println("\nmaterials/ 里有这些图:");
        for (int i = 0; i < images.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + images.get(i).getFileName());
        }
        System.out.print("输入序号用这张图,或回车让 AI 生成: ");
        String answer = sc.hasNextLine() ? sc.nextLine().trim() : "";
        if (answer.isBlank()) return null;
        try {
            int idx = Integer.parseInt(answer) - 1;
            if (idx >= 0 && idx < images.size()) return images.get(idx);
        } catch (NumberFormatException ignored) {
        }
        System.out.println("无效输入,改用 AI 生成");
        return null;
    }

    /** 文生图 + 用户确认 */
    static Path generateKeyframeWithReview(Scanner sc, String description) throws Exception {
        ImageClient image = new ImageClient();
        while (true) {
            System.out.println("\n① 文生图:生成关键帧(约 10~30 秒)...");
            String keyframeUrl = image.textToImage(description);
            Path keyframe = Path.of(OUTPUT_DIR, "keyframe-" + timestamp() + ".jpg");
            image.download(keyframeUrl, keyframe);
            System.out.println("   关键帧已生成: " + keyframe);
            System.out.println("   (可打开这个文件查看)");
            System.out.print("   满意吗?(y 满意 / r 重新生成 / n 取消): ");
            String answer = sc.hasNextLine() ? sc.nextLine().trim() : "";
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) return keyframe;
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no") || answer.equals("取消")) return null;
            // r 或其他 → 重新生成
        }
    }

    /** 长片:逐镜头生成 + 拼接 */
    static void generateAndAssemble(List<Shot> shots, String stamp) throws Exception {
        VideoClient video = new VideoClient();
        List<Path> clips = new ArrayList<>();
        for (Shot s : shots) {
            int duration = s.duration() >= 8 ? 10 : 5;
            System.out.println("\n【生成镜头 " + s.shot() + "/" + shots.size() + "】"
                    + s.shotType() + ",时长 " + duration + " 秒");
            String prompt = s.description() + "," + s.action();
            String taskId = video.submit(prompt, duration);
            String url = video.waitForVideo(taskId);
            Path out = Path.of(OUTPUT_DIR, "shot-" + s.shot() + "-" + stamp + ".mp4");
            video.download(url, out);
            clips.add(out);
            System.out.println("   镜头 " + s.shot() + " 完成 → " + out);
        }
        System.out.println("\n拼接成片...");
        VideoAssembler assembler = new VideoAssembler(FFMPEG);
        Path finalVideo = Path.of(OUTPUT_DIR, "final-" + stamp + ".mp4");
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

    static String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
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
