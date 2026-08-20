package com.example.animation;

import java.util.List;

/**
 * 主程序:把整个流程串起来。
 * 故事 → 调 DeepSeek 拆分镜 → 解析成镜头对象 → 打印。
 */
public class Main {

    public static void main(String[] args) throws Exception {
        String story = args.length > 0 ? String.join(" ", args) : "一个女孩在雨天撑伞走过街道";

        String systemPrompt = "你是一个动画导演。请把用户给的故事拆成分镜脚本,"
                + "用 JSON 数组输出。每个镜头包含字段:"
                + "shot(镜头号)、shotType(景别,如远景/中景/特写)、"
                + "description(画面描述)、action(画面动作)。"
                + "只输出 JSON,不要任何解释。";

        DeepSeekClient client = new DeepSeekClient();
        StoryboardParser parser = new StoryboardParser();

        System.out.println("故事: " + story);
        System.out.println("正在请求 DeepSeek 拆分镜...\n");

        String reply = client.chat(systemPrompt, story);
        List<Shot> shots = parser.parse(reply);

        System.out.println("拆出了 " + shots.size() + " 个镜头:\n");
        for (Shot s : shots) {
            System.out.printf("镜头 %d(%s)\n  画面: %s\n  动作: %s\n\n",
                    s.shot(), s.shotType(), s.description(), s.action());
        }
    }
}
