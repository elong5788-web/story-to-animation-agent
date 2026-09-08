package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 多镜头分镜:基于小说拆解,把情节分段设计成多个镜头,写回 ctx.storyBoard。
 */
public class StoryboardDesigner extends Skill<StoryBoard> {

    public StoryboardDesigner(DeepSeekClient ds) {
        super(ds);
    }

    @Override
    protected StoryBoard generateOnce(Context ctx, String feedback) throws Exception {
        NovelBreakdown b = ctx.novelBreakdown();
        String prompt = Prompts.storyboard().formatted(b.characters(), b.style(), b.world().toText());
        String user = ctx.input() + (feedback == null ? "" : feedback);
        String reply = ds.chatJson(prompt, user);
        JsonNode n = mapper.readTree(reply);
        List<Shot> shots = new ArrayList<>();
        for (JsonNode s : n.path("shots")) {
            shots.add(new Shot(
                    s.path("time").asText(""),
                    s.path("scene").asText(""),
                    s.path("framing").asText(""),
                    s.path("action").asText(""),
                    s.path("camera").asText(""),
                    s.path("emotion").asText("")));
        }
        return new StoryBoard(shots);
    }

    @Override
    protected void show(StoryBoard board, Console console) {
        console.println("\n===== 多镜头分镜(请审查)=====");
        for (int i = 0; i < board.shots().size(); i++) {
            Shot s = board.shots().get(i);
            console.println("镜头" + (i + 1) + " [" + s.time() + "] " + s.framing());
            console.println("  场景:" + s.scene());
            console.println("  动作:" + s.action());
            console.println("  运镜:" + s.camera() + " | 情绪:" + s.emotion());
        }
    }

    @Override
    protected String alternateHint() {
        return "\n(请给一套不同的分镜)";
    }

    @Override
    protected String feedbackText(String userText, StoryBoard board) {
        return "\n\n(上次分镜:" + board.shots() + ",用户意见:\n" + userText + "\n请重新设计分镜。)";
    }

    @Override
    protected void store(Context ctx, StoryBoard board) {
        ctx.setStoryBoard(board);
    }
}
