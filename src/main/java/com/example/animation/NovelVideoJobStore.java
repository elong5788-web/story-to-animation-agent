package com.example.animation;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

/** 小说视频任务的 JSON 快照读写。 */
public final class NovelVideoJobStore {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NovelVideoJobStore() {}

    public static Path path(String stamp) {
        return Path.of("output", "novel-job-" + stamp + ".json");
    }

    public static void save(String stamp, String input, NovelBreakdown breakdown, StoryBoard storyboard) throws Exception {
        Path job = path(stamp);
        Files.createDirectories(job.getParent());
        MAPPER.writeValue(job.toFile(), new NovelVideoJob(input, breakdown, storyboard));
    }

    public static NovelVideoJob load(String stamp) throws Exception {
        Path job = path(stamp);
        if (!Files.isRegularFile(job)) {
            throw new IllegalArgumentException("找不到任务快照: " + job.toAbsolutePath());
        }
        NovelVideoJob loaded = MAPPER.readValue(job.toFile(), NovelVideoJob.class);
        if (loaded.breakdown() == null || loaded.storyboard() == null || loaded.storyboard().shots() == null
                || loaded.storyboard().shots().isEmpty()) {
            throw new IllegalStateException("任务快照缺少拆解或分镜数据: " + job.toAbsolutePath());
        }
        return loaded;
    }
}
