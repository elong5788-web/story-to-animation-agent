package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Scanner;

/**
 * 情节定位步骤:搜索知识识别桥段/角色/风格,并让用户审查。
 */
public class Localizer {

    private static final ObjectMapper mapper = new ObjectMapper();

    /** 生成定位 + 审查,返回最终定位(取消返回 null) */
    public static Localization run(Scanner sc, String input) throws Exception {
        DeepSeekClient ds = new DeepSeekClient();
        Localization loc = generate(ds, input);
        return review(sc, ds, input, loc);
    }

    static Localization generate(DeepSeekClient ds, String input) throws Exception {
        String reply = ds.chat(Prompts.localize(), input);
        String json = TextUtil.stripCodeFence(reply);
        try {
            JsonNode n = mapper.readTree(json);
            return new Localization(
                    n.path("work").asText(""), n.path("scene").asText(""),
                    n.path("characters").asText(""), n.path("plot").asText(""),
                    n.path("iconicVisual").asText(""), n.path("style").asText(""));
        } catch (Exception e) {
            return new Localization(json, "", "", "", "", "");
        }
    }

    static Localization review(Scanner sc, DeepSeekClient ds, String input, Localization loc) throws Exception {
        while (true) {
            System.out.println("\n===== 情节定位(请审查)=====");
            System.out.println("【作品】" + loc.work());
            System.out.println("【桥段】" + loc.scene());
            System.out.println("【角色】" + loc.characters());
            System.out.println("【剧情】" + loc.plot());
            System.out.println("【名场面】" + loc.iconicVisual());
            System.out.println("【风格】" + loc.style());
            System.out.println("  · y=满意 / n=取消 / r=换一个 / 其他=修改意见(尤其可改风格)");
            System.out.print("> ");
            String answer = sc.hasNextLine() ? sc.nextLine().trim() : "";
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) return loc;
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no") || answer.equals("取消")) {
                System.out.println("已取消。");
                return null;
            }
            if (answer.equalsIgnoreCase("r") || answer.equals("换一个")) {
                loc = generate(ds, input + "\n(请定位一个不同的桥段)");
                continue;
            }
            String feedback = (answer + "\n" + InputHandler.readRest(sc)).trim();
            loc = generate(ds, input + "\n\n(上次定位:[" + loc.toText() + "],用户意见:\n" + feedback + "\n请重新定位。)");
        }
    }
}
