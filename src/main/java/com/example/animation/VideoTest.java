package com.example.animation;

import java.nio.file.Path;

/**
 * 单独测试视频生成:用一句提示词生成一段视频,下载到本地。
 * 先跑通这个,再把它接进 agent 里批量生成。
 */
public class VideoTest {

    public static void main(String[] args) throws Exception {
        String prompt = args.length > 0 ? String.join(" ", args)
                : "一个女孩在雨天撑伞走过街道,电影感,远景";

        System.out.println("测试:生成一段视频");
        System.out.println("提示词: " + prompt + "\n");

        VideoClient client = new VideoClient();

        System.out.println("提交任务...");
        String taskId = client.submit(prompt);
        System.out.println("任务 id: " + taskId + "\n");

        System.out.println("等待生成(视频要几十秒到几分钟)...");
        String url = client.waitForVideo(taskId);
        System.out.println("\n视频地址: " + url);

        Path out = Path.of("test-video.mp4");
        client.download(url, out);
        System.out.println("已下载到: " + out.toAbsolutePath());
    }
}
