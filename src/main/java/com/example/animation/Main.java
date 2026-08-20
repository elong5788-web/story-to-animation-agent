package com.example.animation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 主程序:完整流水线。
 * 故事 → 拆镜(re-plan 质检)→ 逐镜头生成视频 → ffmpeg 拼接成片。
 */
public class Main {

    /** ffmpeg 可执行文件路径(按实际安装位置改) */
    static final String FFMPEG = "C:/Users/elong258/ffmpeg/bin/ffmpeg.exe";

    public static void main(String[] args) throws Exception {
        String story = args.length > 0 ? String.join(" ", args) : "一个女孩在雨天撑伞走过街道";

        // 1. 拆分镜(带 re-plan 质检循环)
        StoryboardAgent agent = new StoryboardAgent();
        List<Shot> shots = agent.plan(story);

        // 2. 逐镜头生成视频
        VideoClient video = new VideoClient();
        List<Path> clips = new ArrayList<>();
        for (Shot s : shots) {
            System.out.println("\n【生成镜头 " + s.shot() + "/" + shots.size() + "】"
                    + s.shotType() + ",时长 " + s.duration() + " 秒");

            // 把时长归一化到 Seedance 支持的 5 或 10 秒
            int duration = s.duration() >= 8 ? 10 : 5;
            String prompt = s.description() + "," + s.action();

            String taskId = video.submit(prompt, duration);
            String url = video.waitForVideo(taskId);
            Path out = Path.of("shot-" + s.shot() + ".mp4");
            video.download(url, out);
            clips.add(out);
            System.out.println("   镜头 " + s.shot() + " 完成 → " + out);
        }

        // 3. 拼接成片
        System.out.println("\n拼接成片...");
        VideoAssembler assembler = new VideoAssembler(FFMPEG);
        Path finalVideo = Path.of("final.mp4");
        assembler.concat(clips, finalVideo);
        System.out.println("成片已生成: " + finalVideo.toAbsolutePath());
    }
}
