package com.example.animation;

import java.util.List;

/**
 * RAG 检索评测:用一组「查询 → 期望题材」的用例,算 precision@k 和 MRR。
 * 以语料的 genre 标签当"真值":top-k 里 genre 命中期望题材的占比 = 精度。
 * 可单独跑 main 看真实指标,也可当回归测试。
 */
public class RagEval {

    /** 评测用例:查询 + 期望命中的题材标签 */
    public record Case(String query, String expectedGenre) {}

    /** 结果:平均 precision@k 和平均 MRR(倒数排名) */
    public record Metrics(double precisionAtK, double mrr) {}

    public static List<Case> cases() {
        return List.of(
                new Case("水墨 武侠 写意 留白 泼墨", "武侠"),
                new Case("科幻 赛博朋克 霓虹 雨夜都市", "科幻"),
                new Case("都市 写实 自然光 生活感", "都市"),
                new Case("动漫 二次元 赛璐璐 厚涂", "动漫"),
                new Case("仙侠 玄幻 修仙 粒子 仙气", "仙侠"));
    }

    public static Metrics eval(Retriever r, int k) {
        double prec = 0, mrr = 0;
        int valid = 0;
        for (Case c : cases()) {
            List<RetrievalResult> res = r.retrieve(c.query, k);
            if (res.isEmpty()) {
                continue;
            }
            valid++;
            int hit = 0;
            double rr = 0;
            for (int i = 0; i < res.size(); i++) {
                if (res.get(i).chunk().genre().contains(c.expectedGenre)) {
                    hit++;
                    if (rr == 0) {
                        rr = 1.0 / (i + 1);
                    }
                }
            }
            prec += (double) hit / k;
            mrr += rr;
        }
        if (valid == 0) {
            return new Metrics(0, 0);
        }
        return new Metrics(prec / valid, mrr / valid);
    }

    public static void main(String[] args) {
        Retriever r = new Retriever();
        Metrics m = eval(r, 3);
        System.out.printf("RAG 评测: precision@3 = %.3f, MRR = %.3f%n", m.precisionAtK(), m.mrr());
        for (Case c : cases()) {
            System.out.println("\n=== " + c.query() + " (期望题材:" + c.expectedGenre() + ") ===");
            for (RetrievalResult rr : r.retrieve(c.query(), 3)) {
                String text = rr.chunk().text();
                String snippet = text.length() > 28 ? text.substring(0, 28) + "…" : text;
                System.out.printf("  genre=%-4s style=%-4s | %s%n",
                        rr.chunk().genre(), rr.chunk().style(), snippet);
            }
        }
    }
}
