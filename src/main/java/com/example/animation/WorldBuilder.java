package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 世界观:生成氛围(强调/弱化分层),写回 ctx.world。
 */
public class WorldBuilder extends Skill<WorldBuilding> {

    public WorldBuilder(DeepSeekClient ds) {
        super(ds);
    }

    @Override
    protected WorldBuilding generateOnce(Context ctx, String feedback) throws Exception {
        String user = ctx.withLoc() + (feedback == null ? "" : feedback);
        String reply = ds.chatJson(Prompts.world(), user);
        JsonNode n = mapper.readTree(reply);
        return new WorldBuilding(
                n.path("tone").asText(""), n.path("scale").asText(""),
                n.path("mystery").asText(""), n.path("wonder").asText(""),
                n.path("palette").asText(""), n.path("lighting").asText(""),
                n.path("weather").asText(""), n.path("culture").asText(""));
    }

    @Override
    protected void show(WorldBuilding world, Console console) {
        console.println("\n===== 氛围设定(重点维度,请审查)=====");
        console.println("【色调】" + world.palette());
        console.println("【光线】" + world.lighting());
        console.println("【尺度】" + world.scale());
        console.println("【奇观】" + world.wonder());
        console.println("(次要氛围已自动生成:基调[" + world.tone() + "] 神秘感[" + world.mystery()
                + "] 气象[" + world.weather() + "] 文化[" + world.culture() + "])");
    }

    @Override
    protected String alternateHint() {
        return "\n(请给一个完全不同的世界)";
    }

    @Override
    protected String feedbackText(String userText, WorldBuilding world) {
        return "\n\n(用户补充的世界观设定:\n" + userText + ")";
    }

    @Override
    protected void store(Context ctx, WorldBuilding world) {
        ctx.setWorld(world);
    }
}
