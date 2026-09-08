package com.example.animation;

import java.util.List;

/**
 * 编排器:按注册顺序执行各 skill,遇到取消就停止。
 * skill 列表即"流水线",加减能力只改这里,不动 skill 内部。
 */
public class Agent {
    private final List<Skill<?>> pipeline;

    public Agent(List<Skill<?>> pipeline) {
        this.pipeline = pipeline;
    }

    public void run(Context ctx, Console console) throws Exception {
        for (Skill<?> skill : pipeline) {
            skill.execute(ctx, console);
            if (ctx.cancelled()) {
                console.println("已停止。");
                return;
            }
        }
    }
}
