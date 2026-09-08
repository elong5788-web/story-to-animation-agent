package com.example.animation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrieverTest {

    @Test
    void 核心词权重应高于宽泛词_赛博朋克召回科幻而非都市() {
        CorpusEntry sciFi = new CorpusEntry("现成提示词", "科幻", "无", "写实", 7, "科幻角色素材", "科幻内容");
        CorpusEntry urban = new CorpusEntry("现成提示词", "都市", "无", "写实", 7, "服装展示素材", "服装内容");
        Retriever r = Retriever.of(List.of(sciFi, urban));

        List<String> result = r.retrieve("科幻赛博朋克(霓虹冷调/全息/雨夜都市)", 2, 50);

        assertFalse(result.isEmpty(), "应召回语料");
        assertTrue(result.get(0).contains("科幻"),
                "科幻(权重3)应排在都市(权重1)前面,实际第一是:" + result.get(0));
    }

    @Test
    void 无匹配时用方法论兜底() {
        CorpusEntry method = new CorpusEntry("方法论", "通用", "通用", "通用", 9, "运镜方法论", "运镜内容");
        CorpusEntry template = new CorpusEntry("结构模板", "通用", "无", "通用", 4, "分镜表模板", "模板内容");
        Retriever r = Retriever.of(List.of(method, template));

        List<String> result = r.retrieve("像素风(颗粒像素/复古游戏)", 1, 50);

        assertFalse(result.isEmpty(), "应兜底召回");
        assertTrue(result.get(0).contains("运镜"), "无匹配时应兜底方法论,实际:" + result.get(0));
    }
}
