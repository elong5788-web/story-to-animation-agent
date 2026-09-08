package com.example.animation;

/**
 * 语料库中的一条记录(对应 corpus.json 里的一项)。
 */
public record CorpusEntry(String type, String genre, String camera, String style,
                          int score, String summary, String text) {}
