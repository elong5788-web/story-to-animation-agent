package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 小说拆解:把小说拆成 角色卡 + 画风 + 世界观 + 情节分段,写回 ctx.novelBreakdown。
 */
public class NovelParser extends Skill<NovelBreakdown> {

    public NovelParser(DeepSeekClient ds) {
        super(ds);
    }

    @Override
    protected NovelBreakdown generateOnce(Context ctx, String feedback) throws Exception {
        String user = ctx.input() + (feedback == null ? "" : feedback);
        String reply = ds.chatJson(Prompts.novel(), user);
        JsonNode n = mapper.readTree(reply);
        String characters = n.path("characters").asText("");
        if (characters.isBlank()) {
            // 角色卡是锁一致性的根基,为空直接判失败,交给基类的重试机制再问一次
            throw new IllegalStateException("角色卡为空");
        }
        WorldBuilding world = new WorldBuilding(
                n.path("world").path("tone").asText(""),
                n.path("world").path("scale").asText(""),
                n.path("world").path("mystery").asText(""),
                n.path("world").path("wonder").asText(""),
                n.path("world").path("palette").asText(""),
                n.path("world").path("lighting").asText(""),
                n.path("world").path("weather").asText(""),
                n.path("world").path("culture").asText(""));
        List<String> segments = new ArrayList<>();
        for (JsonNode seg : n.path("segments")) {
            if (!seg.asText("").isBlank()) {
                segments.add(seg.asText(""));
            }
        }
        return new NovelBreakdown(
                characters,
                n.path("style").asText(""),
                world,
                segments);
    }

    @Override
    protected void show(NovelBreakdown b, Console console) {
        console.println("\n===== 故事拆解(请审查)=====");
        console.println("【画风】" + b.style());
        console.println("【角色】" + b.characters());
        console.println("【世界观】" + b.world().toText());
        console.println("【情节分段】(" + b.segments().size() + " 段)");
        for (int i = 0; i < b.segments().size(); i++) {
            console.println("  " + (i + 1) + ". " + b.segments().get(i));
        }
    }

    @Override
    protected String alternateHint() {
        return "\n(请重新拆解这个故事)";
    }

    @Override
    protected String feedbackText(String userText, NovelBreakdown b) {
        return "\n\n(上次拆解:角色[" + b.characters() + "],画风[" + b.style()
                + "],用户意见:\n" + userText + "\n请重新拆解。)";
    }

    @Override
    protected void store(Context ctx, NovelBreakdown b) {
        ctx.setNovelBreakdown(b);
    }
}
