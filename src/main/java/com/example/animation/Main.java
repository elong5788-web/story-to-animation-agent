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

    /** DeepSeek 角色:专业提示词工程师,把一句话扩写成高质量视频提示词 */
    static final String EXPANDER_PROMPT = "你是一个专业的 AI 视频提示词工程师。"
            + "请把用户的一句话,扩写成一段高质量的文生视频提示词,必须包含这些要素:"
            + "主体(谁/什么,外貌细节)、动作(在做什么)、场景(在哪,环境细节)、"
            + "镜头(景别如远景/特写,运镜如缓慢推近/平移)、光影氛围、"
            + "风格(如电影感/写实/水墨/赛博朋克)、画质(如4K、超高清、细节丰富)。"
            + "直接输出这段提示词(80~150字),不要任何解释或多余内容。";

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

        // 1. 确定关键帧(用户给路径/网址,或 AI 生成)
        String keyframeInput = askForKeyframe(sc, description);
        if (keyframeInput == null) return;   // 用户取消

        // 2. 图生视频:文字是"动作描述",告诉视频模型怎么动
        System.out.println("\n② 图生视频:让关键帧动起来(约 1~3 分钟)...");
        VideoClient video = new VideoClient();
        String taskId = video.submitImageToVideo(keyframeInput, description, duration);
        String url = video.waitForVideo(taskId);
        Path out = Path.of(OUTPUT_DIR, "video-" + stamp + ".mp4");
        video.download(url, out);
        System.out.println("视频已生成: " + out.toAbsolutePath());
    }

    /** 关键帧来源:粘贴图片路径/网址,或序号选 materials/,或回车 AI 生成。返回 URL/dataURL */
    static String askForKeyframe(Scanner sc, String description) throws Exception {
        List<Path> materialsImages = listMaterialsImages();
        while (true) {
            if (!materialsImages.isEmpty()) {
                System.out.println("\nmaterials/ 里有图:");
                for (int i = 0; i < materialsImages.size(); i++) {
                    System.out.println("  " + (i + 1) + ". " + materialsImages.get(i).getFileName());
                }
            }
            System.out.println("关键帧来源:");
            System.out.println("  · 粘贴图片文件路径 或 网址(https://...)");
            if (!materialsImages.isEmpty()) System.out.println("  · 输入序号,选 materials/ 里的图");
            System.out.println("  · 直接回车 → 让 AI 文生图");
            System.out.print("> ");
            String answer = sc.hasNextLine() ? sc.nextLine().trim() : "";

            if (answer.isBlank()) {
                return aiKeyframeWithReview(sc, description);   // 可能返回 null(取消)
            }
            if (answer.startsWith("http://") || answer.startsWith("https://")) {
                return answer;   // 直接用网址
            }
            // 序号 → materials/
            if (!materialsImages.isEmpty()) {
                try {
                    int idx = Integer.parseInt(answer) - 1;
                    if (idx >= 0 && idx < materialsImages.size()) {
                        return ImageClient.toDataUrl(materialsImages.get(idx));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            // 本地文件路径
            Path p = Path.of(answer);
            if (Files.exists(p) && Files.isRegularFile(p)) {
                return ImageClient.toDataUrl(p);
            }
            System.out.println("  没找到这个路径或网址,再试一次\n");
        }
    }

    /** 列出 materials/ 里的图片 */
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

    /** AI 文生图 + 用户确认,返回 dataURL(取消返回 null) */
    static String aiKeyframeWithReview(Scanner sc, String description) throws Exception {
        ImageClient image = new ImageClient();
        while (true) {
            System.out.println("\n① 文生图:生成关键帧(约 10~30 秒)...");
            String url = image.textToImage(description);
            Path keyframe = Path.of(OUTPUT_DIR, "keyframe-" + timestamp() + ".jpg");
            image.download(url, keyframe);
            System.out.println("   关键帧已生成: " + keyframe);
            System.out.println("   (可打开这个文件查看)");
            System.out.print("   满意吗?(y 满意 / r 重新生成 / n 取消): ");
            String answer = sc.hasNextLine() ? sc.nextLine().trim() : "";
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) {
                return ImageClient.toDataUrl(keyframe);
            }
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no") || answer.equals("取消")) {
                return null;
            }
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
