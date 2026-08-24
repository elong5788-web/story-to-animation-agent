package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Scanner;

/**
 * 镜头设计步骤:基于定位+世界观,拆 10 个画面/动作维度,并让用户审查。
 */
public class ShotDesigner {

    private static final ObjectMapper mapper = new ObjectMapper();

    /** 生成 10 维度 + 审查,返回最终设计(取消返回 null) */
    public static ShotDesign run(Scanner sc, String input, WorldBuilding world) throws Exception {
        DeepSeekClient ds = new DeepSeekClient();
        ShotDesign design = generate(ds, input, world);
        return review(sc, ds, input, world, design);
    }

    static ShotDesign generate(DeepSeekClient ds, String input, WorldBuilding world) throws Exception {
        String prompt = Prompts.expand().formatted(world.toCoreText());
        String reply = ds.chat(prompt, input);
        String json = TextUtil.stripCodeFence(reply);
        try {
            JsonNode n = mapper.readTree(json);
            return new ShotDesign(
                    n.path("subject").asText(""), n.path("clothing").asText(""),
                    n.path("setting").asText(""), n.path("style").asText(""),
                    n.path("quality").asText(""), n.path("camera").asText(""),
                    n.path("narrative").asText(""), n.path("emotion").asText(""),
                    n.path("action").asText(""), n.path("negative").asText(""));
        } catch (Exception e) {
            return new ShotDesign(json, "", "", "", "", "", "", "", "", "");
        }
    }

    static ShotDesign review(Scanner sc, DeepSeekClient ds, String input, WorldBuilding world, ShotDesign d) throws Exception {
        while (true) {
            System.out.println("\n===== 画面与动作维度(请审查)=====");
            System.out.println("【主体】" + d.subject());
            System.out.println("【服装】" + d.clothing());
            System.out.println("【场景】" + d.setting());
            System.out.println("【风格】" + d.style());
            System.out.println("【画质】" + d.quality());
            System.out.println("【运镜】" + d.camera());
            System.out.println("【叙事】" + d.narrative());
            System.out.println("【情绪】" + d.emotion());
            System.out.println("【动作】" + d.action());
            System.out.println("【负面约束】" + d.negative());
            System.out.println("  · y=满意 / n=取消 / r=换一个 / 其他=修改意见");
            System.out.print("> ");
            String answer = sc.hasNextLine() ? sc.nextLine().trim() : "";
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) return d;
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no") || answer.equals("取消")) {
                System.out.println("已取消。");
                return null;
            }
            if (answer.equalsIgnoreCase("r") || answer.equals("换一个")) {
                d = generate(ds, input + "\n(请给和上次不同的版本)", world);
                continue;
            }
            String feedback = (answer + "\n" + InputHandler.readRest(sc)).trim();
            d = generate(ds, input + "\n\n(上次维度:主体[" + d.subject() + "],服装[" + d.clothing()
                    + "],场景[" + d.setting() + "],风格[" + d.style() + "],画质[" + d.quality()
                    + "],运镜[" + d.camera() + "],叙事[" + d.narrative() + "],情绪[" + d.emotion()
                    + "],动作[" + d.action() + "],负面[" + d.negative()
                    + "],用户意见:\n" + feedback + "\n请重新生成。)", world);
        }
    }
}
