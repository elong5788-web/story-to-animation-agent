package com.example.animation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShotDesignTest {

    @Test
    void keyframePrompt_按画风角色场景画质顺序拼接() {
        ShotDesign.Timeline t = new ShotDesign.Timeline("0-2秒", "中景", "动作", "推", "情绪");
        ShotDesign d = new ShotDesign("角色", "场景", "水墨", "画质", List.of(t), "声音", "负面");
        assertEquals("水墨,角色,场景,画质", d.keyframePrompt());
    }

    @Test
    void motionPrompt_渲染时间轴() {
        ShotDesign.Timeline t = new ShotDesign.Timeline("0-2秒", "中景", "动作", "推", "情绪");
        ShotDesign d = new ShotDesign("角色", "场景", "水墨", "画质", List.of(t), "声音", "负面");
        assertTrue(d.motionPrompt().contains("0-2秒 中景 动作 推 情绪"), "实际:" + d.motionPrompt());
    }
}
