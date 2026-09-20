package com.example.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RagEvalTest {

    @Test
    void 真实语料上precision至少三分之一() {
        // 用 BM25-only 工厂(不触发真实 embedding),单测要快、不联网
        RagEval.Metrics m = RagEval.eval(Retriever.of(ChunkStore.load()), 3);
        // 保守下限:平均 top-3 里至少 1 条命中题材(防检索彻底跑偏)
        assertTrue(m.precisionAtK() >= 1.0 / 3.0,
                "precision@3 过低:" + m.precisionAtK());
    }
}
