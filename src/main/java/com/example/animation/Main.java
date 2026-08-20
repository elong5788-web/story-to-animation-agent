package com.example.animation;

import java.util.List;

/**
 * 主程序:输入故事 → 交给 agent 循环拆镜+质检 → 打印最终分镜。
 */
public class Main {

    public static void main(String[] args) throws Exception {
        String story = args.length > 0 ? String.join(" ", args) : "一个女孩在雨天撑伞走过街道";

        System.out.println("故事: " + story);
        System.out.println("开始分镜(re-plan 质检循环)...\n");

        StoryboardAgent agent = new StoryboardAgent();
        List<Shot> shots = agent.plan(story);

        System.out.println("=== 最终分镜 ===");
        for (Shot s : shots) {
            System.out.printf("镜头 %d(%s)\n  画面: %s\n  动作: %s\n\n",
                    s.shot(), s.shotType(), s.description(), s.action());
        }
    }
}
