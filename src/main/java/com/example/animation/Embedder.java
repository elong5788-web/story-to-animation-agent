package com.example.animation;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本向量化接口:默认实现走火山 Ark(EmbeddingClient),测试可注入 lambda 假实现。
 */
@FunctionalInterface
public interface Embedder {

    float[] embed(String text) throws Exception;

    /** 批量向量化(默认逐个调用;EmbeddingClient 覆写为一次请求) */
    default List<float[]> embedBatch(List<String> texts) throws Exception {
        List<float[]> out = new ArrayList<>(texts.size());
        for (String t : texts) {
            out.add(embed(t));
        }
        return out;
    }
}
