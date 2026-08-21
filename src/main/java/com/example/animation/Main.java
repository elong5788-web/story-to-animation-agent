package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 主程序:输入一句话 → 选模式 → DeepSeek 拆成「画面描述 + 动作描述」→ 用户审查
 * → 文生图(画面)→ 图生视频(动作)→ 出片。
 */
public class Main {

    static final String FFMPEG = "C:/Users/elong258/ffmpeg/bin/ffmpeg.exe";
    static final String OUTPUT_DIR = "output";

    /** DeepSeek 角色:把一句话拆成「画面描述 + 动作描述」两段,JSON 输出 */
    static final String EXPANDER_PROMPT = "你是一个专业的 AI 视频提示词工程师。"
            + "请把用户的一句话,拆成两段提示词,用 JSON 输出,格式严格为:{\"scene\":\"画面描述\",\"motion\":\"动作描述\"}。"
            + "scene(画面描述,给文生图用):主体、场景、光影、风格、画质关键词,约80字。"
            + "motion(动作描述,给图生视频用):动作、镜头运镜,约30字。"
            + "只输出 JSON,不要任何解释或多余内容。";

    /** 两段提示词 */
    record VideoPrompt(String scene, String motion) {
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        Files.createDirectories(Path.of(OUTPUT_DIR));
        String stamp = timestamp();

        // 1. 输入画面描述
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
            VideoPrompt prompt = generatePrompt(ds, input);
            prompt = reviewPromptLoop(sc, ds, input, prompt);
            if (prompt == null) return;
            generateShortVideo(sc, prompt, stamp);
        }
    }

    /** 让 DeepSeek 拆出「画面描述 + 动作描述」 */
    static VideoPrompt generatePrompt(DeepSeekClient ds, String input) throws Exception {
        String reply = ds.chat(EXPANDER_PROMPT, input);
        String json = StoryboardParser.stripCodeFence(reply);
        try {
            JsonNode node = mapper.readTree(json);
            String scene = node.path("scene").asText("");
            String motion = node.path("motion").asText("");
            if (scene.isBlank()) scene = json;
            if (motion.isBlank()) motion = scene;
            return new VideoPrompt(scene, motion);
        } catch (Exception e) {
            // JSON 解析失败:整段文字同时当画面和动作用
            return new VideoPrompt(json, json);
        }
    }

    /** 短片:让用户审查两段提示词,可反复修改 */
    static VideoPrompt reviewPromptLoop(Scanner sc, DeepSeekClient ds, String input, VideoPrompt prompt) throws Exception {
        while (true) {
            System.out.println("\n【画面描述】(文生图用,画什么):");
            System.out.println("  " + prompt.scene());
            System.out.println("【动作描述】(图生视频用,怎么动):");
            System.out.println("  " + prompt.motion());
            System.out.println("  · y=满意 / n=取消 / r=换一个 / 其他=修改意见");
            System.out.print("> ");
            String answer = sc.hasNextLine() ? sc.nextLine().trim() : "";
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) return prompt;
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no") || answer.equals("取消")) {
                System.out.println("已取消。");
                return null;
            }
            if (answer.equalsIgnoreCase("r") || answer.equals("换一个")) {
                prompt = generatePrompt(ds, input + "\n(请给和上次完全不同的新版本)");
                continue;
            }
            prompt = generatePrompt(ds, input + "\n\n(上次的画面描述:[" + prompt.scene()
                    + "],动作描述:[" + prompt.motion() + "],用户意见:" + answer + ",请重新拆分。)");
        }
    }

    /** 长片:让用户审查分镜,可反复修改 */
    static List<Shot> reviewShotsLoop(Scanner sc, StoryboardAgent agent, String input, List<Shot> shots) throws Exception {
        while (true) {
            printShots(shots);
            System.out.println("  · y=满意 / n=取消 / 其他=修改意见重新拆镜");
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
    static void generateShortVideo(Scanner sc, VideoPrompt prompt, String stamp) throws Exception {
        int duration = Config.getInt("DURATION", 5);

        // 1. 确定关键帧:用「画面描述」文生图
        String keyframeInput = askForKeyframe(sc, prompt.scene());
        if (keyframeInput == null) return;

        // 2. 图生视频:关键帧 + 「动作描述」
        System.out.println("\n② 图生视频:让关键帧动起来(约 1~3 分钟)...");
        VideoClient video = new VideoClient();
        String taskId = video.submitImageToVideo(keyframeInput, prompt.motion(), duration);
        String url = video.waitForVideo(taskId);
        Path out = Path.of(OUTPUT_DIR, "video-" + stamp + ".mp4");
        video.download(url, out);
        System.out.println("视频已生成: " + out.toAbsolutePath());
    }

    /** 关键帧来源:粘贴路径/网址,或序号选 materials/,或回车 AI 生成 */
    static String askForKeyframe(Scanner sc, String scene) throws Exception {
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
                return aiKeyframeWithReview(sc, scene);
            }
            if (answer.startsWith("http://") || answer.startsWith("https://")) {
                return answer;
            }
            if (!materialsImages.isEmpty()) {
                try {
                    int idx = Integer.parseInt(answer) - 1;
                    if (idx >= 0 && idx < materialsImages.size()) {
                        return ImageClient.toDataUrl(materialsImages.get(idx));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            Path p = Path.of(answer);
            if (Files.exists(p) && Files.isRegularFile(p)) {
                return ImageClient.toDataUrl(p);
            }
            System.out.println("  没找到这个路径或网址,再试一次\n");
        }
    }

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
    static String aiKeyframeWithReview(Scanner sc, String scene) throws Exception {
        ImageClient image = new ImageClient();
        while (true) {
            System.out.println("\n① 文生图:生成关键帧(约 10~30 秒)...");
            String url = image.textToImage(scene);
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
