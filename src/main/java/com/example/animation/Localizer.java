package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 情节定位:识别桥段/角色/风格,写回 ctx.loc。
 */
public class Localizer extends Skill<Localization> {

    public Localizer(DeepSeekClient ds) {
        super(ds);
    }

    @Override
    protected Localization generateOnce(Context ctx, String feedback) throws Exception {
        String user = ctx.input() + (feedback == null ? "" : feedback);
        String reply = ds.chatJson(Prompts.localize(), user);
        JsonNode n = mapper.readTree(reply);
        return new Localization(
                n.path("work").asText(""), n.path("scene").asText(""),
                n.path("characters").asText(""), n.path("plot").asText(""),
                n.path("iconicVisual").asText(""), n.path("style").asText(""));
    }

    @Override
    protected void show(Localization loc, Console console) {
        console.println("\n===== 情节定位(请审查)=====");
        console.println("【作品】" + loc.work());
        console.println("【桥段】" + loc.scene());
        console.println("【角色】" + loc.characters());
        console.println("【剧情】" + loc.plot());
        console.println("【名场面】" + loc.iconicVisual());
        console.println("【风格】" + loc.style());
    }

    @Override
    protected String alternateHint() {
        return "\n(请定位一个不同的桥段)";
    }

    @Override
    protected String feedbackText(String userText, Localization loc) {
        return "\n\n(上次定位:[" + loc.toText() + "],用户意见:\n" + userText + "\n请重新定位。)";
    }

    @Override
    protected void store(Context ctx, Localization loc) {
        ctx.setLoc(loc);
    }
}
