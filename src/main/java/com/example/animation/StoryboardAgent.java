package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 分镜 agent:负责"拆镜 → 质检 → 不满意就重新拆(re-plan)"的循环。
 * 这是 agent 的核心:不是一次成型,而是反复检查、修正,直到满意。
 */
public class StoryboardAgent {

    /** 最多重试次数,防止死循环 */
    private static final int MAX_ATTEMPTS = 3;

    private static final String DIRECTOR_PROMPT = "你是一个动画导演。请把用户给的故事拆成分镜脚本,"
            + "用 JSON 数组输出。每个镜头包含字段:"
            + "shot(镜头号)、shotType(景别,如远景/中景/特写)、"
            + "description(画面描述)、action(画面动作)。"
            + "只输出 JSON,不要任何解释。";

    private static final String REVIEWER_PROMPT = "你是一个严格的动画质检员。请检查下面这个分镜脚本是否合格:\n%s\n"
            + "检查标准:1) 镜头之间景别和动作是否连贯;2) 是否适合做成视频;3) 有没有明显逻辑问题。"
            + "只输出 JSON:{\"approved\": true 或 false, \"feedback\": \"通过就写'通过',不通过就指出具体问题\"}";

    private final DeepSeekClient client = new DeepSeekClient();
    private final StoryboardParser parser = new StoryboardParser();
    private final ObjectMapper mapper = new ObjectMapper();

    /** 主流程:拆镜 → 质检 → 不满意就重新拆,直到通过或达到次数上限 */
    public List<Shot> plan(String story) throws Exception {
        List<Shot> shots = generate(story, null);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Review review = review(shots);
            if (review.approved()) {
                System.out.println("✅ 第 " + attempt + " 次质检通过,分镜合格。\n");
                return shots;
            }
            System.out.println("⚠️ 第 " + attempt + " 次质检未通过,问题: " + review.feedback());
            System.out.println("   → 根据反馈重新拆分镜(re-plan)...\n");
            shots = generate(story, review.feedback());
        }

        System.out.println("(达到最大重试次数 " + MAX_ATTEMPTS + ",采用最后一次结果)\n");
        return shots;
    }

    /** 生成分镜;feedback 为 null 表示首次生成,否则是根据反馈重新生成 */
    private List<Shot> generate(String story, String feedback) throws Exception {
        String systemPrompt = DIRECTOR_PROMPT;
        if (feedback != null && !feedback.isBlank()) {
            systemPrompt += "\n上一次的分镜被质检否定了,反馈如下:\n" + feedback + "\n请针对这些问题重新拆分。";
        }
        String reply = client.chat(systemPrompt, story);
        return parser.parse(reply);
    }

    /** 让大模型自己检查分镜质量 */
    private Review review(List<Shot> shots) throws Exception {
        String shotsJson = mapper.writeValueAsString(shots);
        String reply = client.chat(REVIEWER_PROMPT.formatted(shotsJson), "请检查。");
        String json = StoryboardParser.stripCodeFence(reply);
        JsonNode node = mapper.readTree(json);
        boolean approved = "true".equalsIgnoreCase(node.path("approved").asText());
        String feedback = node.path("feedback").asText("");
        return new Review(approved, feedback);
    }

    /** 质检结果 */
    public record Review(boolean approved, String feedback) {
    }
}
