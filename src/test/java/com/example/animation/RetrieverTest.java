package com.example.animation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrieverTest {

    @Test
    void 空查询返回空() {
        Retriever r = Retriever.of(List.of(new Chunk("科幻", "推", "紧张", "写实", 7, "s", "正文")));
        assertTrue(r.retrieve("", 3).isEmpty());
        assertTrue(r.retrieve(null, 3).isEmpty());
    }

    @Test
    void 正文相关度优先_科幻查询召回科幻而非都市() {
        Chunk sciFi = new Chunk("科幻", "推", "紧张", "写实", 7, "科幻", "赛博朋克霓虹雨夜,全息投影,冷色调");
        Chunk urban = new Chunk("都市", "推", "平静", "写实", 7, "都市", "城市街道阳光,自然光,生活感");
        Retriever r = Retriever.of(List.of(sciFi, urban));

        List<RetrievalResult> res = r.retrieve("科幻赛博朋克 霓虹冷调 雨夜", 2);

        assertFalse(res.isEmpty(), "应召回");
        assertEquals("赛博朋克霓虹雨夜,全息投影,冷色调", res.get(0).chunk().text(), "科幻应排第一");
    }

    @Test
    void 标签命中加权_水墨画风片应召回水墨() {
        Chunk ink = new Chunk("古风", "推", "温柔", "水墨", 8, "水墨", "远山淡墨,留白,写意");
        Chunk oil = new Chunk("西方", "推", "温柔", "油画", 8, "油画", "厚重笔触,古典构图");
        Retriever r = Retriever.of(List.of(ink, oil));

        List<RetrievalResult> res = r.retrieve("水墨 写意 留白", 2);

        assertFalse(res.isEmpty());
        assertEquals("远山淡墨,留白,写意", res.get(0).chunk().text(), "水墨应排第一");
    }

    @Test
    void 质量分先验_同内容高分排前() {
        Chunk low = new Chunk("通用", "无", "无", "通用", 5, "s", "完全相同的正文内容");
        Chunk high = new Chunk("通用", "无", "无", "通用", 9, "s", "完全相同的正文内容");
        Retriever r = Retriever.of(List.of(low, high));

        List<RetrievalResult> res = r.retrieve("正文内容", 2);

        assertEquals(2, res.size());
        assertEquals(9, res.get(0).chunk().score(), "高质量分应排前");
    }
}
