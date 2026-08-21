package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 主程序:
 * 输入一句话 → 【前置】定世界观(氛围,用户必须满意)→ 拆画面/动作 → 文生图 → 图生视频。
 */
public class Main {

    static final String FFMPEG = "C:/Users/elong258/ffmpeg/bin/ffmpeg.exe";
    static final String OUTPUT_DIR = "output";

    /** 世界观(氛围)8 个子维度 */
    record WorldBuilding(String tone, String scale, String mystery, String wonder,
                         String palette, String lighting, String weather, String culture) {
        String toText() {
            return "世界观基调:" + tone
                    + "\n宏大尺度:" + scale
                    + "\n神秘感:" + mystery
                    + "\n独有奇观:" + wonder
                    + "\n色调系统:" + palette
                    + "\n光线性质:" + lighting
                    + "\n自然气象:" + weather
                    + "\n文化符号:" + culture;
        }
    }

    /** 两段提示词 */
    record VideoPrompt(String scene, String motion) {
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    /** 世界观构建师:生成氛围 8 个子维度,每个都要写详实 */
    static final String WORLD_PROMPT = "你是一个奇幻世界观构建师。"
            + "请根据用户的一句话(可能是小说片段),构建这个世界的「氛围」,用 JSON 输出。"
            + "每个字段都要写得详实、具体、有画面感、有明显异世界感,每个字段至少 3~4 句话,不要一句话带过。"
            + "格式严格为:{\"tone\":\"世界观基调\",\"scale\":\"宏大尺度\",\"mystery\":\"神秘感\",\"wonder\":\"独有奇观\","
            + "\"palette\":\"色调系统\",\"lighting\":\"光线性质\",\"weather\":\"自然气象\",\"culture\":\"文化符号\"}。"
            + "各字段写什么:"
            + "tone=这个世界是什么体系(仙侠/玄幻/神话/末世)及核心法则;"
            + "scale=这个世界有多宏大(巨大建筑、无边景观、人渺小的对比);"
            + "mystery=哪里让人感到未知和敬畏(雾、光晕、不可名状之物);"
            + "wonder=这个世界独有、人类世界没有的奇观;"
            + "palette=整个世界的配色方案(主色辅色,像电影调色一样具体);"
            + "lighting=光从哪来、什么质感(神性光、体积光、逆光);"
            + "weather=超自然天气现象;"
            + "culture=建筑、文字、图腾、种族等文化标志。"
            + "只输出 JSON,不要任何解释。";

    /** 提示词工程师:在已锁定世界观基础上,拆画面 + 动作 */
    static final String EXPANDER_PROMPT = "你是一个专业的 AI 视频提示词工程师。"
            + "已锁定的世界观氛围如下:\n%s\n"
            + "请在这个世界观基础上,把用户的一句话拆成两段提示词,用 JSON 输出,格式严格为:{\"scene\":\"画面描述\",\"motion\":\"动作描述\"}。"
            + "scene(画面描述,给文生图用):主体外貌细节、服装、场景环境、延续上述世界观氛围、艺术风格、构图,"
            + "并附英文画质关键词(如 4K, cinematic lighting, ultra-detailed, masterpiece),约150字,务必具体专业。"
            + "motion(动作描述,给图生视频用):动作过程、镜头运镜(景别+运动方式)、节奏,约50字。"
            + "只输出 JSON,不要任何解释或多余内容。";

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        Files.createDirectories(Path.of(OUTPUT_DIR));
        String stamp = timestamp();

        // 1. 输入
        String fromFile = readStory(args);
        System.out.println("当前 story.txt: " + fromFile);
        System.out.println("输入画面(回车用上面的;可粘贴多行文字,或输入 .txt 文件路径;空行结束): ");
        String typed = readRest(sc);
        String input = resolveInput(typed, fromFile);
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

            // 前置:定世界观,必须让用户满意
            WorldBuilding world = generateWorld(ds, input);
            world = reviewWorldLoop(sc, ds, input, world);
            if (world == null) return;

            // 后续:基于世界观,拆画面 + 动作
            VideoPrompt prompt = generatePrompt(ds, input, world);
            prompt = reviewPromptLoop(sc, ds, input, world, prompt);
            if (prompt == null) return;

            generateShortVideo(sc, prompt, stamp);
        }
    }

    /** 生成世界观(氛围 8 子维度) */
    static WorldBuilding generateWorld(DeepSeekClient ds, String input) throws Exception {
        String reply = ds.chat(WORLD_PROMPT, input);
        String json = StoryboardParser.stripCodeFence(reply);
        try {
            JsonNode n = mapper.readTree(json);
            return new WorldBuilding(
                    n.path("tone").asText(""), n.path("scale").asText(""),
                    n.path("mystery").asText(""), n.path("wonder").asText(""),
                    n.path("palette").asText(""), n.path("lighting").asText(""),
                    n.path("weather").asText(""), n.path("culture").asText(""));
        } catch (Exception e) {
            return new WorldBuilding(json, "", "", "", "", "", "", "");
        }
    }

    /** 世界观审查:用户必须满意,可加文字填补优化 */
    static WorldBuilding reviewWorldLoop(Scanner sc, DeepSeekClient ds, String input, WorldBuilding world) throws Exception {
        while (true) {
            System.out.println("\n===== 世界观设定(请审查,满意才继续)=====");
            System.out.println("【基调】" + world.tone());
            System.out.println("【尺度】" + world.scale());
            System.out.println("【神秘感】" + world.mystery());
            System.out.println("【奇观】" + world.wonder());
            System.out.println("【色调】" + world.palette());
            System.out.println("【光线】" + world.lighting());
            System.out.println("【气象】" + world.weather());
            System.out.println("【文化】" + world.culture());
            System.out.println("  · y = 满意,锁定这个世界");
            System.out.println("  · r = 换一个完全不同的世界");
            System.out.println("  · 其他 = 你补充的文字,用来优化这个世界");
            System.out.println("  · n = 取消");
            System.out.print("> ");
            String answer = sc.hasNextLine() ? sc.nextLine().trim() : "";
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) return world;
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no") || answer.equals("取消")) {
                System.out.println("已取消。");
                return null;
            }
            if (answer.equalsIgnoreCase("r") || answer.equals("换一个")) {
                world = generateWorld(ds, input + "\n(请给一个完全不同的世界)");
                continue;
            }
            // 其他 = 用户补充文字(可多行,空行结束)
            String feedback = (answer + "\n" + readRest(sc)).trim();
            world = generateWorld(ds, input + "\n\n(用户补充的世界观设定:\n" + feedback + ")");
        }
    }

    /** 基于世界观,拆画面 + 动作 */
    static VideoPrompt generatePrompt(DeepSeekClient ds, String input, WorldBuilding world) throws Exception {
        String prompt = EXPANDER_PROMPT.formatted(world.toText());
        String reply = ds.chat(prompt, input);
        String json = StoryboardParser.stripCodeFence(reply);
        try {
            JsonNode node = mapper.readTree(json);
            String scene = node.path("scene").asText("");
            String motion = node.path("motion").asText("");
            if (scene.isBlank()) scene = json;
            if (motion.isBlank()) motion = scene;
            return new VideoPrompt(scene, motion);
        } catch (Exception e) {
            return new VideoPrompt(json, json);
        }
    }

    /** 短片:审查画面 + 动作两段提示词 */
    static VideoPrompt reviewPromptLoop(Scanner sc, DeepSeekClient ds, String input, WorldBuilding world, VideoPrompt prompt) throws Exception {
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
                prompt = generatePrompt(ds, input + "\n(请给和上次不同的版本)", world);
                continue;
            }
            String feedback = (answer + "\n" + readRest(sc)).trim();
            prompt = generatePrompt(ds, input + "\n\n(上次画面:[" + prompt.scene()
                    + "],动作:[" + prompt.motion() + "],用户意见:\n" + feedback + "\n请重新拆分。)", world);
        }
    }

    /** 长片:审查分镜 */
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

        String keyframeInput = askForKeyframe(sc, prompt.scene());
        if (keyframeInput == null) return;

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
            System.out.println("  · 粘贴图片文件路径 或 网址(https://... 结尾是 .jpg/.png 的图片)");
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

    /** 读多行文字,直到空行(用于粘贴长文字/小说片段) */
    static String readRest(Scanner sc) {
        StringBuilder sb = new StringBuilder();
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            if (line.isBlank()) break;
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    /** 解析输入:空则用默认;是文件路径则读文件;否则当粘贴文字 */
    static String resolveInput(String typed, String fromFile) throws Exception {
        if (typed.isBlank()) return fromFile;
        // 单行且是存在的文件 → 读文件内容
        if (!typed.contains("\n")) {
            try {
                Path p = Path.of(normalizePath(typed));
                if (Files.exists(p) && Files.isRegularFile(p)) {
                    System.out.println("(已读取文件: " + p + ")");
                    return readTextFile(p);
                }
            } catch (Exception ignored) {
            }
        }
        // 否则当作粘贴文字,存进 story.txt
        Files.writeString(Path.of("story.txt"), typed, StandardCharsets.UTF_8);
        return typed;
    }

    /** 把 Git Bash 风格路径 /c/Users/... 转成 Windows 的 C:/Users/... */
    static String normalizePath(String s) {
        if (s.length() >= 3 && s.charAt(0) == '/' && s.charAt(2) == '/') {
            return Character.toUpperCase(s.charAt(1)) + ":" + s.substring(2);
        }
        return s;
    }

    /** 读文本文件:自动识别 UTF-8/GBK,太长截断到前 3000 字 */
    static String readTextFile(Path p) throws Exception {
        byte[] bytes = Files.readAllBytes(p);
        String content = new String(bytes, StandardCharsets.UTF_8);
        // 含替换字符(U+FFFD)说明不是 UTF-8,改用 GBK
        if (content.contains("\uFFFD")) {
            content = new String(bytes, Charset.forName("GBK"));
        }
        content = content.trim();
        if (content.length() > 3000) {
            System.out.println("(文件太大,只取前 3000 字)");
            content = content.substring(0, 3000);
        }
        return content;
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
