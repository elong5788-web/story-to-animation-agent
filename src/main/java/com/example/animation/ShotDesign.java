package com.example.animation;

/**
 * 画面/动作的 8 个细分维度。
 * scene() 合成画面提示词(给文生图),motion() 合成动作提示词(给图生视频)。
 */
public record ShotDesign(String subject, String clothing, String setting, String style, String quality,
                         String camera, String narrative, String action) {
    /** 画面提示词:画风放最前面 */
    public String scene() {
        return style + "," + subject + "," + clothing + "," + setting + "," + quality;
    }

    public String motion() {
        return action + "," + camera + "," + narrative;
    }
}
