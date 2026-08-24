package com.example.animation;

/**
 * 画面/动作的 10 个细分维度(基于好莱坞分镜 + AI 视频最佳实践)。
 * scene() 给文生图,motion() 给图生视频。
 */
public record ShotDesign(
        String subject, String clothing, String setting, String style, String quality,
        String camera, String narrative, String emotion, String action, String negative
) {
    /** 画面提示词:画风放最前面 */
    public String scene() {
        return style + "," + subject + "," + clothing + "," + setting + "," + quality;
    }

    /** 动作提示词:动作时间线 + 运镜 + 情绪 */
    public String motion() {
        return action + "," + camera + "," + emotion;
    }
}
