package com.example.animation;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Agent 的一个能力(skill):读上下文 → 生成(带重试) → 可选人工审查 → 写回上下文。
 * 交互走注入的 Console,不碰控制台;审查由 needsReview() 开关。
 * 子类实现 generateOnce/show/alternateHint/feedbackText/store 五个方法,
 * 审查循环、重试、失败降级由基类统一处理。
 */
public abstract class Skill<T> {

    protected final DeepSeekClient ds;
    protected static final ObjectMapper mapper = new ObjectMapper();

    protected Skill(DeepSeekClient ds) {
        this.ds = ds;
    }

    /** 执行:生成 → 审查 → 写回。取消/失败时标记 ctx.cancel() 并返回 null。 */
    public final T execute(Context ctx, Console console) throws Exception {
        T result;
        try {
            result = generateWithRetry(ctx, console, null);
        } catch (Exception e) {
            console.println("生成失败: " + e.getMessage());
            ctx.cancel();
            return null;
        }
        if (needsReview()) {
            result = review(ctx, console, result);
            if (result == null) {
                ctx.cancel();
                return null;
            }
        }
        store(ctx, result);
        return result;
    }

    /** 是否需要人工审查。默认 true;自动化时可覆写返回 false 一路跑到底。 */
    protected boolean needsReview() {
        return true;
    }

    /** 单次生成 + 解析。feedback 为 null 表示首次。失败抛异常。 */
    protected abstract T generateOnce(Context ctx, String feedback) throws Exception;

    /** 打印结果给用户审查。 */
    protected abstract void show(T result, Console console);

    /** "换一个"的提示片段。 */
    protected abstract String alternateHint();

    /** "用户意见 + 上次结果"的提示片段。 */
    protected abstract String feedbackText(String userText, T current);

    /** 把结果写回上下文(如 loc/world/design)。 */
    protected abstract void store(Context ctx, T result);

    private T generateWithRetry(Context ctx, Console console, String feedback) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return generateOnce(ctx, feedback);
            } catch (Exception e) {
                last = e;
                if (attempt < 3) {
                    console.println("   (第 " + attempt + " 次失败,自动重试: " + e.getMessage() + ")");
                    // 把失败原因当补充反馈喂给下一次,让模型知道该修什么(如"角色卡为空")
                    feedback = (feedback == null ? "" : feedback)
                            + "\n\n(上次生成失败:" + e.getMessage() + ",请修正后重新输出)";
                }
            }
        }
        throw new IllegalStateException("连续 3 次失败,最后错误: " + last.getMessage(), last);
    }

    private T review(Context ctx, Console console, T current) throws Exception {
        while (true) {
            show(current, console);
            console.println("  · y=满意 / n=取消 / r=换一个 / 其他=输入修改意见(回车提交)");
            console.print("> ");
            String answer = console.readLine();
            if (answer == null) return null;  // 输入流结束(非交互),安全退出,避免死循环
            answer = answer.trim();
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) return current;
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no") || answer.equals("取消")) {
                console.println("已取消。");
                return null;
            }
            String feedback;
            if (answer.equalsIgnoreCase("r") || answer.equals("换一个")) {
                feedback = alternateHint();
            } else {
                feedback = feedbackText(answer, current);  // 单行意见即可;原来的 readRest() 会让用户以为打字没用(要按两次回车)
            }
            try {
                current = generateWithRetry(ctx, console, feedback);
            } catch (Exception e) {
                console.println("生成失败: " + e.getMessage());
                console.print("  重试(r) / 取消(其他): ");
                String retry = console.readLine();
                if (retry == null || !retry.trim().equalsIgnoreCase("r")) return null;
            }
        }
    }
}
