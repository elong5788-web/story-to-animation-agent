package com.example.animation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NovelVideoJobStoreTest {

    @Test
    void 小说任务快照往返保留分镜和在途任务号(@TempDir Path tempDir) throws Exception {
        var world = new WorldBuilding("沉静", "辽阔", "未知", "月蚀", "蓝灰", "逆光", "细雨", "古典");
        var breakdown = new NovelBreakdown("旅人", "水墨", world, java.util.List.of("旅人穿过雨夜"));
        var board = new StoryBoard(java.util.List.of(
                new Shot("0-5s", "石桥", "远景", "缓慢前行", "推镜", "沉思", "电影质感")));
        var original = new NovelVideoJob("故事输入", breakdown, board, Map.of(1, "task-123"));
        Path snapshot = NovelVideoJobStore.path(tempDir, "roundtrip");

        NovelVideoJobStore.save(snapshot, original);
        NovelVideoJob restored = NovelVideoJobStore.load(snapshot);

        assertEquals(original, restored);
    }

    @Test
    void 旧版快照缺少任务号字段时仍可续跑(@TempDir Path tempDir) throws Exception {
        var world = new WorldBuilding("沉静", "辽阔", "未知", "月蚀", "蓝灰", "逆光", "细雨", "古典");
        var breakdown = new NovelBreakdown("旅人", "水墨", world, java.util.List.of("旅人穿过雨夜"));
        var board = new StoryBoard(java.util.List.of(
                new Shot("0-5s", "石桥", "远景", "缓慢前行", "推镜", "沉思", "电影质感")));
        Path snapshot = NovelVideoJobStore.path(tempDir, "legacy");
        var legacy = Map.of("input", "故事输入", "breakdown", breakdown, "storyboard", board);
        Files.writeString(snapshot, new ObjectMapper().writeValueAsString(legacy));

        NovelVideoJob restored = NovelVideoJobStore.load(snapshot);

        assertEquals(Map.of(), restored.videoTaskIds());
        assertEquals(board, restored.storyboard());
    }
}
