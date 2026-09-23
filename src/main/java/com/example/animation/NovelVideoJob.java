package com.example.animation;

/** 持久化的小说视频任务快照,用于失败后按原分镜继续生成。 */
public record NovelVideoJob(String input, NovelBreakdown breakdown, StoryBoard storyboard) {}
