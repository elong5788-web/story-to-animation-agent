package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 镜头设计:基于定位 + 世界观,生成专业 VIDEOPROMPT(含时间轴),写回 ctx.design。
 */
public class ShotDesigner extends Skill<ShotDesign> {

    private final Retriever retriever;

    public ShotDesigner(DeepSeekClient ds, Retriever retriever) {
        super(ds);
        this.retriever = retriever;
    }

    @Override
    protected ShotDesign generateOnce(Context ctx, String feedback) throws Exception {
        String prompt = Prompts.expand().formatted(ctx.loc().style(), ctx.loc().characters(), ctx.world().toCoreText());
        String user = ctx.withLoc() + (feedback == null ? "" : feedback);
        // RAG: 检索语料库里的相关范例做 few-shot(query 富化:风格+桥段+剧情+核心氛围)
        String ragQuery = String.join(" ", ctx.loc().style(), ctx.loc().scene(), ctx.loc().plot(), ctx.world().toCoreText());
        List<RetrievalResult> refs = retriever.retrieve(ragQuery, 3);
        if (!refs.isEmpty()) {
            StringBuilder sb = new StringBuilder(user);
            sb.append("\n\n[参考范例(来自高质量语料库,可借鉴运镜/情绪/氛围写法,但不要照抄)]\n");
            for (RetrievalResult r : refs) {
                sb.append("\n---\n").append(r.chunk().text());
            }
            user = sb.toString();
        }
        String reply = ds.chatJson(prompt, user);
        JsonNode n = mapper.readTree(reply);
        List<ShotDesign.Timeline> timeline = new ArrayList<>();
        for (JsonNode seg : n.path("timeline")) {
            timeline.add(new ShotDesign.Timeline(
                    seg.path("time").asText(""),
                    seg.path("framing").asText(""),
                    seg.path("action").asText(""),
                    seg.path("camera").asText(""),
                    seg.path("emotion").asText("")));
        }
        return new ShotDesign(
                n.path("character").asText(""),
                n.path("scene").asText(""),
                n.path("style").asText(""),
                n.path("quality").asText(""),
                timeline,
                n.path("sound").asText(""),
                n.path("negative").asText(""));
    }

    @Override
    protected void show(ShotDesign d, Console console) {
        console.println("\n===== 分镜设计(请审查)=====");
        console.println("【角色】" + d.character());
        console.println("【场景】" + d.scene());
        console.println("【画风】" + d.style());
        console.println("【画质】" + d.quality());
        console.println("【画面内容(时间轴)】");
        for (ShotDesign.Timeline t : d.timeline()) {
            console.println("  " + t.time() + " " + t.framing() + " | " + t.action()
                    + " | 运镜:" + t.camera() + " | 情绪:" + t.emotion());
        }
        console.println("【声音】" + d.sound());
        console.println("【负面约束】" + d.negative());
    }

    @Override
    protected String alternateHint() {
        return "\n(请保留故事、角色和画风，但明显改变上次方案的场景调度、动作设计、景别组合与运镜节奏；不要只替换形容词。)";
    }

    @Override
    protected String feedbackText(String userText, ShotDesign d) {
        return "\n\n(上次版本:角色[" + d.character() + "],场景[" + d.scene()
                + "],画风[" + d.style() + "],画质[" + d.quality() + "],时间轴[" + d.timeline()
                + "],用户意见:\n" + userText + "\n请重新生成。)";
    }

    @Override
    protected void store(Context ctx, ShotDesign d) {
        ctx.setDesign(d);
    }
}
