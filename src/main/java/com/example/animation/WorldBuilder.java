package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Scanner;

/**
 * 世界观步骤:生成氛围(强调/弱化分层),并让用户审查。
 */
public class WorldBuilder {

    private static final ObjectMapper mapper = new ObjectMapper();

    /** 生成世界观 + 审查,返回最终世界观(取消返回 null) */
    public static WorldBuilding run(Scanner sc, String input) throws Exception {
        DeepSeekClient ds = new DeepSeekClient();
        WorldBuilding world = generate(ds, input);
        return review(sc, ds, input, world);
    }

    static WorldBuilding generate(DeepSeekClient ds, String input) throws Exception {
        String reply = ds.chat(Prompts.world(), input);
        String json = TextUtil.stripCodeFence(reply);
        try {
            JsonNode n = mapper.readTree(json);
            return new WorldBuilding(
                    n.path("tone").asText(""), n.path("scale").asText(""),
                    n.path("mystery").asText(""), n.path("wonder").asText(""),
                    n.path("palette").asText(""), n.path("lighting").asText(""),
                    n.path("weather").asText(""), n.path("culture").asText(""));
        } catch (Exception e) {
            return new WorldBuilding(json, "", "", "", "", "", "", "");
        }
    }

    static WorldBuilding review(Scanner sc, DeepSeekClient ds, String input, WorldBuilding world) throws Exception {
        while (true) {
            System.out.println("\n===== 氛围设定(重点维度,请审查)=====");
            System.out.println("【色调】" + world.palette());
            System.out.println("【光线】" + world.lighting());
            System.out.println("【尺度】" + world.scale());
            System.out.println("【奇观】" + world.wonder());
            System.out.println("(次要氛围已自动生成:基调[" + world.tone() + "] 神秘感[" + world.mystery()
                    + "] 气象[" + world.weather() + "] 文化[" + world.culture() + "])");
            System.out.println("  · y = 满意,锁定这个世界");
            System.out.println("  · r = 换一个完全不同的世界");
            System.out.println("  · 其他 = 你补充的文字,用来优化这个世界");
            System.out.println("  · n = 取消");
            System.out.print("> ");
            String answer = sc.hasNextLine() ? sc.nextLine().trim() : "";
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) return world;
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no") || answer.equals("取消")) {
                System.out.println("已取消。");
                return null;
            }
            if (answer.equalsIgnoreCase("r") || answer.equals("换一个")) {
                world = generate(ds, input + "\n(请给一个完全不同的世界)");
                continue;
            }
            String feedback = (answer + "\n" + InputHandler.readRest(sc)).trim();
            world = generate(ds, input + "\n\n(用户补充的世界观设定:\n" + feedback + ")");
        }
    }
}
