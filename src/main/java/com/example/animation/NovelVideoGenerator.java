package com.example.animation;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 读小说模式成片:逐镜头图生视频 → ffmpeg 拼接成片。
 * 一致性两条腿:
 *   1. 每个镜头的关键帧 prompt 写死画风+角色卡(基础锁定,无需额外模型);
 *   2. USE_REFERENCE_IMAGE=true 时,再用一张角色定妆参考图(reference_image)贯穿所有镜头,锁得更死
 *      (需 Ark 开通参考图模型 doubao-seedance-1-0-lite-i2v-250428)。
 */
public class NovelVideoGenerator {

    public static void generate(Console console, NovelBreakdown b, StoryBoard board, String stamp) throws Exception {
        int duration = Config.getInt("DURATION", 5);
        boolean useReference = "true".equalsIgnoreCase(Config.get("USE_REFERENCE_IMAGE"));
        int shots = board.shots().size();
        console.println("\n生成成片:共 " + shots + " 个镜头,每镜头约 1~3 分钟,别走开...");

        ImageClient image = new ImageClient();
        VideoClient video = new VideoClient();

        // 角色参考图(可选)
        String reference = null;
        if (useReference) {
            console.println("\n生成角色定妆参考图(锁定全片角色)...");
            reference = generateCharacterReference(console, image, b, stamp);
        }

        // 逐镜头
        List<Path> clips = new ArrayList<>();
        for (int i = 0; i < shots; i++) {
            Shot s = board.shots().get(i);
            Path clip = Path.of("output", "clip-" + stamp + "-" + (i + 1) + ".mp4");
            if (Files.exists(clip) && Files.size(clip) > 0) {
                console.println("\n[" + (i + 1) + "/" + shots + "] 已有镜头片段，跳过生成: " + clip.getFileName());
                if (NovelVideoJobStore.videoTaskId(stamp, i + 1) != null) {
                    NovelVideoJobStore.clearVideoTaskId(stamp, i + 1);
                }
                clips.add(clip);
                continue;
            }
            console.println("\n[" + (i + 1) + "/" + shots + "] 生成镜头 " + (i + 1) + "...");

            String taskId = NovelVideoJobStore.videoTaskId(stamp, i + 1);
            if (taskId == null || taskId.isBlank()) {
                String firstFrame = generateKeyframe(console, image, b, s, i + 1, stamp);
                String motion = String.join(",", s.framing(), s.action(), s.camera());
                console.println("   提交视频任务...");
                taskId = useReference
                        ? video.submitReferenceToVideo(reference, firstFrame, motion, duration)
                        : video.submitImageToVideo(firstFrame, motion, duration);
                NovelVideoJobStore.saveVideoTaskId(stamp, i + 1, taskId);
            } else {
                console.println("   恢复已提交的视频任务: " + taskId);
            }
            String url;
            try {
                url = video.waitForVideo(taskId, console);
            } catch (VideoClient.TerminalVideoTaskException e) {
                NovelVideoJobStore.clearVideoTaskId(stamp, i + 1);
                throw e;
            }

            video.download(url, clip);
            NovelVideoJobStore.clearVideoTaskId(stamp, i + 1);
            clips.add(clip);
            console.println("   镜头 " + (i + 1) + " 完成: " + clip.getFileName());
        }

        console.println("\n拼接成片...");
        Path out = VideoAssembler.concat(clips, Path.of("output", "final-" + stamp + ".mp4"));
        console.println("成片已生成: " + out.toAbsolutePath());
    }

    /** 角色定妆参考图:文生图,画风 + 角色卡 + 定妆照规格 */
    private static String generateCharacterReference(Console console, ImageClient image, NovelBreakdown b, String stamp) throws Exception {
        Path ref = Path.of("output", "character-ref-" + stamp + ".jpg");
        if (!Files.isRegularFile(ref) || Files.size(ref) == 0) {
            String prompt = b.style() + "," + b.characters() + ",角色定妆照,正面全身,高清,无水印";
            String url = image.textToImage(prompt);
            image.download(url, ref);
        } else {
            console.println("   复用角色参考图: " + ref.toAbsolutePath());
        }
        console.println("   角色参考图: " + ref.toAbsolutePath());
        return ImageClient.toDataUrl(ref);
    }

    /** 单镜关键帧:文生图,画风+角色卡+场景+景别+动作,保证与全片一致 */
    private static String generateKeyframe(Console console, ImageClient image, NovelBreakdown b, Shot s, int idx, String stamp) throws Exception {
        Path kf = Path.of("output", "shot-" + stamp + "-" + idx + ".jpg");
        if (Files.isRegularFile(kf) && Files.size(kf) > 0) {
            console.println("   复用已生成的关键帧: " + kf.toAbsolutePath());
            return ImageClient.toDataUrl(kf);
        }

        String quality = s.quality().isBlank() ? "4K,电影级画质" : s.quality();
        String prompt = String.join(",", b.style(), b.characters(), s.scene(), s.framing(), s.action(), quality, "无水印");
        String url = image.textToImage(prompt);
        image.download(url, kf);
        console.println("   关键帧: " + kf.getFileName());
        return ImageClient.toDataUrl(kf);
    }
}
