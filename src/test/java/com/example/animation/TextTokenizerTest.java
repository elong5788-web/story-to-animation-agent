package com.example.animation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTokenizerTest {

    @Test
    void 中文按bigram加单字切分() {
        List<String> t = TextTokenizer.tokenize("水墨");
        assertTrue(t.contains("水墨"), "应有 bigram 水墨,实际:" + t);
        assertTrue(t.contains("水") && t.contains("墨"), "应有单字,实际:" + t);
    }

    @Test
    void 英文按整词小写切分() {
        List<String> t = TextTokenizer.tokenize("ARRI Alexa 35mm");
        assertTrue(t.contains("arri"), "实际:" + t);
        assertTrue(t.contains("alexa"), "实际:" + t);
        assertTrue(t.contains("35mm"), "实际:" + t);
    }

    @Test
    void 标点与空白分隔() {
        List<String> t = TextTokenizer.tokenize("科幻, 赛博朋克");
        assertTrue(t.contains("科幻"), "实际:" + t);
        assertTrue(t.contains("赛博"), "实际:" + t);
    }
}
