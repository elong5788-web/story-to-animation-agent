package com.example.animation;

import java.nio.file.Path;
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
            console.println("\n[" + (i + 1) + "/" + shots + "] 生成镜头 " + (i + 1) + "...");

            String firstFrame = generateKeyframe(console, image, b, s, i + 1, stamp);
            String motion = String.join(",", s.framing(), s.action(), s.camera());
            console.println("   提交视频任务...");
            String taskId = useReference
                    ? video.submitReferenceToVideo(reference, firstFrame, motion, duration)
                    : video.submitImageToVideo(firstFrame, motion, duration);
            String url = video.waitForVideo(taskId, console);

            Path clip = Path.of("output", "clip-" + stamp + "-" + (i + 1) + ".mp4");
            video.download(url, clip);
            clips.add(clip);
            console.println("   镜头 " + (i + 1) + " 完成: " + clip.getFileName());
        }

        console.println("\n拼接成片...");
        Path out = VideoAssembler.concat(clips, Path.of("output", "final-" + stamp + ".mp4"));
        console.println("成片已生成: " + out.toAbsolutePath());
    }

    /** 角色定妆参考图:文生图,画风 + 角色卡 + 定妆照规格 */
    private static String generateCharacterReference(Console console, ImageClient image, NovelBreakdown b, String stamp) throws Exception {
        String prompt = b.style() + "," + b.characters() + ",角色定妆照,正面全身,高清,无水印";
        String url = image.textToImage(prompt);
        Path ref = Path.of("output", "character-ref-" + stamp + ".jpg");
        image.download(url, ref);
        console.println("   角色参考图: " + ref.toAbsolutePath());
        return ImageClient.toDataUrl(ref);
    }

    /** 单镜关键帧:文生图,画风+角色卡+场景+景别+动作,保证与全片一致 */
    private static String generateKeyframe(Console console, ImageClient image, NovelBreakdown b, Shot s, int idx, String stamp) throws Exception {
        String quality = s.quality().isBlank() ? "4K,电影级画质" : s.quality();
        String prompt = String.join(",", b.style(), b.characters(), s.scene(), s.framing(), s.action(), quality, "无水印");
        String url = image.textToImage(prompt);
        Path kf = Path.of("output", "shot-" + stamp + "-" + idx + ".jpg");
        image.download(url, kf);
        console.println("   关键帧: " + kf.getFileName());
        return ImageClient.toDataUrl(kf);
    }
}
