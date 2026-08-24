package com.example.animation;

import java.util.List;

/**
 * 专业 VIDEOPROMPT 结构(对齐 AIGC 教程的 6 段式):
 * 基础设定(character + scene)→ 氛围画质(style + quality)
 * → 画面内容(timeline 时间分段)→ 声音(sound)→ 限制(negative)。
 * keyframePrompt() 给文生图,motionPrompt() 给图生视频。
 *
 * 注意:timeline 是完整的创意分镜(可以写到 12 秒),实际生成的只是
 * DURATION 配置的那几秒「预览短片」,两者各自独立、不强制对齐。
 */
public record ShotDesign(
        String character, String scene, String style, String quality,
        List<Timeline> timeline, String sound, String negative
) {
    /** 时间轴上的一段:景别 + 运镜 + 动作 + 情绪 */
    public record Timeline(String time, String framing, String action, String camera, String emotion) {}

    /** 文生图关键帧提示词:画风放最前 */
    public String keyframePrompt() {
        return style + "," + character + "," + scene + "," + quality;
    }

    /** 图生视频动作提示词:把时间轴渲染成连贯动作 */
    public String motionPrompt() {
        StringBuilder sb = new StringBuilder();
        for (Timeline t : timeline) {
            sb.append(t.time()).append(" ").append(t.framing()).append(" ")
              .append(t.action()).append(" ").append(t.camera()).append(" ")
              .append(t.emotion()).append("; ");
        }
        return sb.toString().trim();
    }
}
