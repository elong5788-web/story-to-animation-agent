package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 向量缓存:把「文本 → 向量」落盘到 output/rag-embeddings.json,语料只 embed 一次。
 */
public class EmbeddingCache {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final Path file = Path.of("output", "rag-embeddings.json");
    private final Map<String, float[]> cache = new HashMap<>();

    public EmbeddingCache() {
        load();
    }

    public boolean contains(String text) {
        return cache.containsKey(text);
    }

    public float[] get(String text) {
        return cache.get(text);
    }

    public void put(String text, float[] vec) {
        cache.put(text, vec);
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(file.toFile());
            root.fields().forEachRemaining(e -> cache.put(e.getKey(), toArray(e.getValue())));
        } catch (Exception e) {
            System.err.println("向量缓存读取失败,将重新 embed: " + e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            mapper.writeValue(file.toFile(), cache);
        } catch (Exception e) {
            System.err.println("向量缓存保存失败: " + e.getMessage());
        }
    }

    private static float[] toArray(JsonNode n) {
        float[] f = new float[n.size()];
        for (int i = 0; i < n.size(); i++) {
            f[i] = (float) n.get(i).asDouble();
        }
        return f;
    }
}
