package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 语料加载:把 corpus.json / corpus_fragments.json 读成统一的 Chunk 列表。
 * 优先用片段级(corpus_fragments.json,更细粒度、带 emotion 标签);
 * 片段没生成或缺失时,降级到文档级(corpus.json,一篇一个 chunk)。
 */
public class ChunkStore {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static List<Chunk> load() {
        List<Chunk> fragments = loadFragments();
        if (!fragments.isEmpty()) {
            System.err.println("RAG 检索单元: 片段级(" + fragments.size() + " 条,corpus_fragments.json)");
            return fragments;
        }
        List<Chunk> docs = loadDocs();
        System.err.println("RAG 检索单元: 文档级(" + docs.size()
                + " 条,corpus.json) — 跑 corpus_tools/fragment_corpus.py 可得到更细粒度");
        return docs;
    }

    /** 片段级:corpus_fragments.json = { 源文件: [ {genre,camera,emotion,style,text}, ... ] } */
    private static List<Chunk> loadFragments() {
        Path p = Path.of("corpus_fragments.json");
        if (!p.toFile().exists()) {
            return List.of();
        }
        try {
            JsonNode root = mapper.readTree(p.toFile());
            Map<String, JsonNode> docMeta = loadDocMeta();  // 片段本身没带 score/summary,从 corpus.json 继承
            List<Chunk> chunks = new ArrayList<>();
            Set<String> seen = new HashSet<>();   // 切分可能产生重复片段,按正文去重
            Iterator<Map.Entry<String, JsonNode>> sources = root.fields();
            while (sources.hasNext()) {
                Map.Entry<String, JsonNode> src = sources.next();
                JsonNode meta = docMeta.get(src.getKey());
                int score = meta == null ? 0 : meta.path("score").asInt(0);
                String summary = meta == null ? "" : meta.path("summary").asText("");
                for (JsonNode f : src.getValue()) {
                    String text = f.path("text").asText("").trim();
                    if (text.isBlank() || !seen.add(text)) {
                        continue;
                    }
                    chunks.add(new Chunk(
                            f.path("genre").asText(""),
                            f.path("camera").asText(""),
                            f.path("emotion").asText(""),
                            f.path("style").asText(""),
                            score,
                            summary,
                            text));
                }
            }
            return chunks;
        } catch (Exception e) {
            System.err.println("加载 corpus_fragments.json 失败,降级文档级: " + e.getMessage());
            return List.of();
        }
    }

    /** 文档级:corpus.json,一篇一个 chunk(emotion 为空),筛掉 score<5 的结构模板/空表垃圾 */
    private static List<Chunk> loadDocs() {
        try {
            JsonNode root = mapper.readTree(Path.of("corpus.json").toFile());
            List<Chunk> chunks = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> it = root.fields();
            while (it.hasNext()) {
                JsonNode n = it.next().getValue();
                if (n.path("score").asInt(0) < 5) {
                    continue;
                }
                chunks.add(new Chunk(
                        n.path("genre").asText(""),
                        n.path("camera").asText(""),
                        "",
                        n.path("style").asText(""),
                        n.path("score").asInt(0),
                        n.path("summary").asText(""),
                        n.path("text").asText("")));
            }
            return chunks;
        } catch (Exception e) {
            System.err.println("加载 corpus.json 失败,RAG 降级为空: " + e.getMessage());
            return List.of();
        }
    }

    /** 源文件名 -> corpus.json 里的那份元数据(供片段继承 score/summary) */
    private static Map<String, JsonNode> loadDocMeta() {
        try {
            JsonNode root = mapper.readTree(Path.of("corpus.json").toFile());
            Map<String, JsonNode> map = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = root.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                map.put(e.getKey(), e.getValue());
            }
            return map;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
