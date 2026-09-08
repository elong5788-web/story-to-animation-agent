package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 语料库检索器:加载 corpus.json(清洗+打分+打标签后的高质量语料),
 * 按加权关键词匹配召回 top k 份相关范例,供分镜设计做 few-shot。
 */
public class Retriever {

    private final List<CorpusEntry> entries;

    /** 关键词权重:题材核心词(3.0) > 画风词(2.0) > 宽泛词(1.0),压低"都市/通用"这类弱信号的噪音。 */
    private static final Map<String, Double> KEYWORD_WEIGHTS = new HashMap<>();
    static {
        for (String kw : List.of("仙侠", "武侠", "科幻", "赛博朋克", "废土", "奇幻", "古风", "动漫", "二次元", "像素")) {
            KEYWORD_WEIGHTS.put(kw, 3.0);
        }
        for (String kw : List.of("水墨", "油画", "赛璐璐", "厚涂", "写实", "工笔")) {
            KEYWORD_WEIGHTS.put(kw, 2.0);
        }
        for (String kw : List.of("都市", "通用", "宫崎骏")) {
            KEYWORD_WEIGHTS.put(kw, 1.0);
        }
    }

    public Retriever() {
        this.entries = loadSafely();
    }

    /** 空检索器:不召回任何语料,用于 A/B 对比(无 RAG 基线)。 */
    public static Retriever empty() {
        return new Retriever(new ArrayList<>());
    }

    /** 用给定的语料构造检索器(测试用)。 */
    public static Retriever of(List<CorpusEntry> entries) {
        return new Retriever(entries);
    }

    private Retriever(List<CorpusEntry> entries) {
        this.entries = entries;
    }

    private static List<CorpusEntry> loadSafely() {
        try {
            return load();
        } catch (Exception e) {
            System.err.println("加载 corpus.json 失败,检索降级为空: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static List<CorpusEntry> load() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(Path.of("corpus.json").toFile());
        List<CorpusEntry> list = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            JsonNode n = it.next().getValue();
            int score = n.path("score").asInt(0);
            if (score < 5) continue;  // 筛掉结构模板/空表垃圾
            list.add(new CorpusEntry(
                    n.path("type").asText(""),
                    n.path("genre").asText(""),
                    n.path("camera").asText(""),
                    n.path("style").asText(""),
                    score,
                    n.path("summary").asText(""),
                    n.path("text").asText("")));
        }
        return list;
    }

    /** 从风格描述里提取命中的关键词。 */
    private static List<String> extractKeywords(String styleQuery) {
        List<String> found = new ArrayList<>();
        for (String kw : KEYWORD_WEIGHTS.keySet()) {
            if (styleQuery != null && styleQuery.contains(kw)) {
                found.add(kw);
            }
        }
        return found;
    }

    /**
     * 加权检索:关键词命中按权重打分(genre 全权重 / style 0.6 / summary 0.3),
     * 叠加质量分微调(score*0.05),召回 top k。匹配不到时用"方法论"类语料低权重兜底。
     */
    public List<String> retrieve(String styleQuery, int k, int maxChars) {
        List<String> keywords = extractKeywords(styleQuery);

        List<ScoredEntry> scored = new ArrayList<>();
        for (CorpusEntry e : entries) {
            double match = 0;
            for (String kw : keywords) {
                double w = KEYWORD_WEIGHTS.getOrDefault(kw, 1.0);
                if (e.genre().contains(kw)) match += w;
                if (e.style().contains(kw)) match += w * 0.6;
                if (e.summary().contains(kw)) match += w * 0.3;
            }
            if (match == 0 && "方法论".equals(e.type())) {
                match = 0.4;  // 无匹配的通用方法论,低权重兜底
            }
            double total = match + e.score() * 0.05;
            scored.add(new ScoredEntry(e, total));
        }

        scored.sort((a, b) -> Double.compare(b.total, a.total));
        if (scored.size() > k) {
            scored = scored.subList(0, k);
        }

        List<String> out = new ArrayList<>();
        for (ScoredEntry se : scored) {
            String text = se.entry.text();
            if (text.length() > maxChars) {
                text = text.substring(0, maxChars);
            }
            out.add(se.entry.summary() + "\n" + text);
        }
        return out;
    }

    private record ScoredEntry(CorpusEntry entry, double total) {}
}
