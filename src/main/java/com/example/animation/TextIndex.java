package com.example.animation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 倒排索引 + 多字段 BM25 打分。
 * 每个 chunk 建两个可检索字段:
 *   - text(权重 1.0):正文,bigram 分词
 *   - tags(权重 0.35):题材/运镜/情绪/画风/摘要的标签词
 * 再叠加质量分(score)做先验。
 * 检索从 O(N) 线性扫降为 O(命中词元 × 平均 posting 数)。
 */
public class TextIndex {

    private static final double K1 = 1.5;
    private static final double B = 0.75;
    private static final double W_TEXT = 1.0;
    private static final double W_TAGS = 0.35;
    private static final double W_QUALITY = 0.05;

    private final List<Chunk> chunks;
    // field -> term -> (chunkId -> tf)
    private final Map<String, Map<String, Map<Integer, Integer>>> postings = new HashMap<>();
    private final int[] textLengths;      // 每 chunk 的 text 字段词元数
    private final double avgTextLength;

    public TextIndex(List<Chunk> chunks) {
        this.chunks = chunks;
        this.textLengths = new int[chunks.size()];
        postings.put("text", new HashMap<>());
        postings.put("tags", new HashMap<>());

        long totalLen = 0;
        for (int i = 0; i < chunks.size(); i++) {
            List<String> textTokens = TextTokenizer.tokenize(chunks.get(i).text());
            textLengths[i] = textTokens.size();
            totalLen += textTokens.size();
            indexField("text", textTokens, i);
            indexField("tags", TextTokenizer.tokenize(chunks.get(i).tags()), i);
        }
        this.avgTextLength = chunks.isEmpty() ? 1.0 : (double) totalLen / chunks.size();
    }

    private void indexField(String field, List<String> tokens, int chunkId) {
        Map<String, Map<Integer, Integer>> fieldPostings = postings.get(field);
        for (String t : tokens) {
            fieldPostings.computeIfAbsent(t, k -> new HashMap<>()).merge(chunkId, 1, Integer::sum);
        }
    }

    /** 给每个 chunk 打 BM25 分(正文 + 标签加权 + 质量分先验),返回与 chunks 对齐的分数数组 */
    public double[] bm25Scores(String query) {
        List<String> qTokens = TextTokenizer.tokenize(query);
        double[] scores = new double[chunks.size()];

        accumulate("text", W_TEXT, qTokens, scores);
        accumulate("tags", W_TAGS, qTokens, scores);

        for (int i = 0; i < chunks.size(); i++) {
            scores[i] += chunks.get(i).score() * W_QUALITY;   // 质量分先验
        }
        return scores;
    }

    private void accumulate(String field, double fieldWeight, List<String> qTokens, double[] scores) {
        Map<String, Map<Integer, Integer>> fieldPostings = postings.get(field);
        int n = chunks.size();
        for (String t : qTokens) {
            Map<Integer, Integer> posting = fieldPostings.get(t);
            if (posting == null) {
                continue;
            }
            double idf = idf(posting.size(), n);
            for (Map.Entry<Integer, Integer> e : posting.entrySet()) {
                int docId = e.getKey();
                int tf = e.getValue();
                // tags 字段词元少、长度差异小,长度归一化按常量简化;text 字段用真实长度
                double dl = field.equals("text") ? textLengths[docId] : 1.0;
                double avgdl = field.equals("text") ? avgTextLength : 1.0;
                double denom = tf + K1 * (1 - B + B * dl / avgdl);
                double bm25 = idf * (tf * (K1 + 1)) / denom;
                scores[docId] += fieldWeight * bm25;
            }
        }
    }

    private static double idf(int df, int n) {
        return Math.log((n - df + 0.5) / (df + 0.5) + 1.0);
    }
}
