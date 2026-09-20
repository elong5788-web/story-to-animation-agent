package com.example.animation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HybridRetrieverTest {

    @Test
    void 语义检索_正文无关时靠向量区分() {
        // 两个片段正文与 query 都无词面匹配(BM25 打平),靠向量语义把 A 排到前面
        Chunk a = new Chunk("通用", "无", "无", "通用", 7, "a", "完全一样的正文A");
        Chunk b = new Chunk("通用", "无", "无", "通用", 7, "b", "完全一样的正文B");
        Embedder fake = text -> {
            if (text.equals("完全一样的正文A")) {
                return new float[]{1f, 0f};
            }
            if (text.equals("完全一样的正文B")) {
                return new float[]{0f, 1f};
            }
            return new float[]{1f, 0f};  // query 与 A 同向
        };
        Retriever r = Retriever.of(List.of(a, b), fake);

        List<RetrievalResult> res = r.retrieve("任意查询", 2);

        assertEquals("完全一样的正文A", res.get(0).chunk().text(), "语义应把 A 排前面");
    }

    @Test
    void 语义失败时回退纯BM25() {
        Chunk a = new Chunk("科幻", "推", "紧张", "写实", 7, "s", "赛博朋克霓虹雨夜");
        Embedder broken = text -> {
            throw new RuntimeException("embedding 服务挂了");
        };
        Retriever r = Retriever.of(List.of(a), broken);

        // 构造时向量化失败回退为空;检索时 embed 失败也回退 BM25,应仍能召回
        List<RetrievalResult> res = r.retrieve("赛博朋克", 3);

        assertEquals(1, res.size());
        assertEquals("赛博朋克霓虹雨夜", res.get(0).chunk().text());
    }
}
