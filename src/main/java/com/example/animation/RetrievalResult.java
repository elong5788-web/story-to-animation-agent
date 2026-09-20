package com.example.animation;

/**
 * 一次检索命中的结果:片段 + 相关性分。
 */
public record RetrievalResult(Chunk chunk, double score) {}
