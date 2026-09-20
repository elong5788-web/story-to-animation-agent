package com.example.animation;

import java.util.List;

/**
 * 检索结果精排:输入 query + 候选片段,返回按相关度从高到低排序的候选下标。
 */
@FunctionalInterface
public interface Reranker {

    List<Integer> rerank(String query, List<Chunk> candidates) throws Exception;
}
