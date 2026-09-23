package com.example.animation;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 小说视频任务的 JSON 快照读写。 */
public final class NovelVideoJobStore {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NovelVideoJobStore() {}

    public static Path path(String stamp) {
        if (stamp == null || !stamp.matches("\\d{8}-\\d{6}")) {
            throw new IllegalArgumentException("任务编号格式应为 yyyyMMdd-HHmmss");
        }
        return path(Path.of("output"), stamp);
    }

    static Path path(Path outputDir, String stamp) { return outputDir.resolve("novel-job-" + stamp + ".json"); }

    public static void save(String stamp, String input, NovelBreakdown breakdown, StoryBoard storyboard) throws Exception {
        save(path(stamp), new NovelVideoJob(input, breakdown, storyboard));
    }

    static void save(Path job, NovelVideoJob value) throws Exception {
        Files.createDirectories(job.getParent());
        writeAtomically(job, value);
    }

    public static NovelVideoJob load(String stamp) throws Exception {
        return load(path(stamp));
    }

    static NovelVideoJob load(Path job) throws Exception {
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

    public static String videoTaskId(String stamp, int shotNumber) throws Exception {
        return load(stamp).videoTaskIds().get(shotNumber);
    }

    public static void saveVideoTaskId(String stamp, int shotNumber, String taskId) throws Exception {
        NovelVideoJob job = load(path(stamp));
        var tasks = new java.util.TreeMap<>(job.videoTaskIds());
        tasks.put(shotNumber, taskId);
        writeAtomically(path(stamp), new NovelVideoJob(job.input(), job.breakdown(), job.storyboard(), tasks));
    }

    public static void clearVideoTaskId(String stamp, int shotNumber) throws Exception {
        NovelVideoJob job = load(path(stamp));
        var tasks = new java.util.TreeMap<>(job.videoTaskIds());
        tasks.remove(shotNumber);
        writeAtomically(path(stamp), new NovelVideoJob(job.input(), job.breakdown(), job.storyboard(), tasks));
    }

    private static void writeAtomically(Path target, NovelVideoJob job) throws Exception {
        Path temp = Files.createTempFile(target.getParent(), "novel-job-", ".tmp");
        try {
            MAPPER.writeValue(temp.toFile(), job);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
