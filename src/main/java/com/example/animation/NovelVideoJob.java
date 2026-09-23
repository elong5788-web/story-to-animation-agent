package com.example.animation;

import java.util.Map;
import java.util.TreeMap;

/** 持久化的小说视频任务快照,用于失败后按原分镜和已提交的视频任务继续生成。 */
public record NovelVideoJob(String input, NovelBreakdown breakdown, StoryBoard storyboard,
                            Map<Integer, String> videoTaskIds) {
    public NovelVideoJob {
        videoTaskIds = videoTaskIds == null ? new TreeMap<>() : new TreeMap<>(videoTaskIds);
    }

    public NovelVideoJob(String input, NovelBreakdown breakdown, StoryBoard storyboard) {
        this(input, breakdown, storyboard, Map.of());
    }
}
