package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 镜头设计步骤:基于定位 + 世界观,生成专业 VIDEOPROMPT(含时间轴),并让用户审查。
 */
public class ShotDesigner {

    private static final ObjectMapper mapper = new ObjectMapper();

    /** 生成分镜设计 + 审查,返回最终设计(取消返回 null) */
    public static ShotDesign run(Scanner sc, String input, Localization loc, WorldBuilding world) throws Exception {
        DeepSeekClient ds = new DeepSeekClient();
        ShotDesign design = generate(ds, input, loc, world);
        return review(sc, ds, input, loc, world, design);
    }

    static ShotDesign generate(DeepSeekClient ds, String input, Localization loc, WorldBuilding world) throws Exception {
        String prompt = Prompts.expand().formatted(loc.style(), loc.characters(), world.toCoreText());
        String reply = ds.chat(prompt, input);
        String json = TextUtil.stripCodeFence(reply);
        try {
            JsonNode n = mapper.readTree(json);
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
        } catch (Exception e) {
            return new ShotDesign(json, "", "", "", List.of(), "", "");
        }
    }

    static ShotDesign review(Scanner sc, DeepSeekClient ds, String input, Localization loc, WorldBuilding world, ShotDesign d) throws Exception {
        while (true) {
            System.out.println("\n===== 分镜设计(请审查)=====");
            System.out.println("【角色】" + d.character());
            System.out.println("【场景】" + d.scene());
            System.out.println("【画风】" + d.style());
            System.out.println("【画质】" + d.quality());
            System.out.println("【画面内容(时间轴)】");
            for (ShotDesign.Timeline t : d.timeline()) {
                System.out.println("  " + t.time() + " " + t.framing() + " | " + t.action()
                        + " | 运镜:" + t.camera() + " | 情绪:" + t.emotion());
            }
            System.out.println("【声音】" + d.sound());
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
                d = generate(ds, input + "\n(请给和上次不同的版本)", loc, world);
                continue;
            }
            String feedback = (answer + "\n" + InputHandler.readRest(sc)).trim();
            d = generate(ds, input + "\n\n(上次版本:角色[" + d.character() + "],场景[" + d.scene()
                    + "],画风[" + d.style() + "],画质[" + d.quality() + "],时间轴[" + d.timeline()
                    + "],用户意见:\n" + feedback + "\n请重新生成。)", loc, world);
        }
    }
}
