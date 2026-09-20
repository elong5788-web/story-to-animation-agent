package com.example.animation;

/**
 * 检索单元:语料库里的一条可召回片段。
 * 片段级(corpus_fragments.json)带 emotion 标签;文档级(corpus.json)降级时 emotion 为空串。
 * score/summary 来自文档级质量打分,片段继承其父文档。
 */
public record Chunk(String genre, String camera, String emotion, String style,
                    int score, String summary, String text) {

    /** 拼出用于 BM25 检索的"标签"伪字段(题材/运镜/情绪/画风/摘要) */
    public String tags() {
        return String.join(" ", genre, camera, emotion, style, summary);
    }
}
