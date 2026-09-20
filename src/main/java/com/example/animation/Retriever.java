package com.example.animation;

import java.util.ArrayList;
import java.util.List;

/**
 * 语料库检索器:BM25(倒排索引)+ 可选语义向量,RRF 融合召回,再可选 LLM 精排。
 * - 纯 BM25:正文 + 标签加权 + 质量分先验
 * - 语义增强(可选):query 与片段都向量化,余弦相似度,和 BM25 用 RRF(倒数排名融合)合并
 * - 重排(可选):召回 top-N 后交给 LLM 按相关度精排
 * 语义/重排开启与否由 config 的 RAG_EMBEDDING / RAG_RERANK 控制;任何一步失败都自动降级,不影响运行。
 */
public class Retriever {

    private static final double RRF_K = 60.0;
    private static final int EMBED_BATCH = 32;
    private static final int RERANK_FACTOR = 3;   // 重排前多召回 k*3 条候选

    private final TextIndex index;
    private final List<Chunk> chunks;
    private final Embedder embedder;          // null = 纯 BM25
    private final List<float[]> vectors;      // 与 chunks 对齐;空 = 无向量
    private final Reranker reranker;          // null = 不重排

    public Retriever() {
        this(ChunkStore.load(), buildEmbedder(), new EmbeddingCache(), buildReranker());
    }

    /** 用给定片段构造(测试用,纯 BM25) */
    public static Retriever of(List<Chunk> chunks) {
        return new Retriever(chunks, null, null, null);
    }

    /** 用给定片段 + 注入的 embedder 构造(测试语义检索) */
    public static Retriever of(List<Chunk> chunks, Embedder embedder) {
        return new Retriever(chunks, embedder, null, null);
    }

    /** 用给定片段 + 注入的 embedder + reranker 构造(测试全链路) */
    public static Retriever of(List<Chunk> chunks, Embedder embedder, Reranker reranker) {
        return new Retriever(chunks, embedder, null, reranker);
    }

    /** 空检索器:不召回任何语料(无 RAG 基线 / 降级场景) */
    public static Retriever empty() {
        return new Retriever(List.of(), null, null, null);
    }

    private Retriever(List<Chunk> chunks, Embedder embedder, EmbeddingCache cache, Reranker reranker) {
        this.chunks = chunks;
        this.index = new TextIndex(chunks);
        this.embedder = embedder;
        this.reranker = reranker;
        this.vectors = (embedder == null || chunks.isEmpty()) ? List.of() : buildVectors(embedder, cache);
    }

    /** 召回与 query 最相关的 top k 个片段;空查询返回空 */
    public List<RetrievalResult> retrieve(String query, int k) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        // 有重排时多召回一些候选,给精排留余地
        int recallK = reranker != null ? Math.min(chunks.size(), Math.max(k * RERANK_FACTOR, 8)) : k;

        double[] bm25 = index.bm25Scores(query);
        List<RetrievalResult> results;
        if (vectors.isEmpty()) {
            results = rankByScore(bm25, recallK);
        } else {
            try {
                float[] qv = embedder.embed(query);
                double[] cos = new double[chunks.size()];
                for (int i = 0; i < chunks.size(); i++) {
                    cos[i] = cosine(qv, vectors.get(i));
                }
                results = rankByRrf(bm25, cos, recallK);
            } catch (Exception e) {
                System.err.println("语义检索失败,回退纯 BM25: " + e.getMessage());
                results = rankByScore(bm25, recallK);
            }
        }

        if (reranker != null && results.size() > k) {
            results = rerank(query, results, k);
        } else if (results.size() > k) {
            results = results.subList(0, k);
        }
        return results;
    }

    // —— 排名 ——

    private List<RetrievalResult> rankByScore(double[] scores, int k) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] > 0) {
                ids.add(i);
            }
        }
        ids.sort((a, b) -> Double.compare(scores[b], scores[a]));
        List<RetrievalResult> out = new ArrayList<>();
        for (int id : ids) {
            if (out.size() >= k) {
                break;
            }
            out.add(new RetrievalResult(chunks.get(id), scores[id]));
        }
        return out;
    }

    private List<RetrievalResult> rankByRrf(double[] bm25, double[] cos, int k) {
        int n = chunks.size();
        List<Integer> byBm25 = rankIds(bm25);
        List<Integer> byCos = rankIds(cos);
        double[] rbm = new double[n];
        double[] rco = new double[n];
        for (int rank = 0; rank < byBm25.size(); rank++) {
            rbm[byBm25.get(rank)] = rank + 1.0;
        }
        for (int rank = 0; rank < byCos.size(); rank++) {
            rco[byCos.get(rank)] = rank + 1.0;
        }

        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (rbm[i] > 0 || rco[i] > 0) {
                ids.add(i);
            }
        }
        ids.sort((a, b) -> Double.compare(rrf(rbm[b], rco[b]), rrf(rbm[a], rco[a])));

        List<RetrievalResult> out = new ArrayList<>();
        for (int id : ids) {
            if (out.size() >= k) {
                break;
            }
            out.add(new RetrievalResult(chunks.get(id), rrf(rbm[id], rco[id])));
        }
        return out;
    }

    /** 用 reranker 对召回的候选精排,取 top-k */
    private List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int k) {
        try {
            List<Chunk> candChunks = new ArrayList<>();
            for (RetrievalResult r : candidates) {
                candChunks.add(r.chunk());
            }
            List<Integer> order = reranker.rerank(query, candChunks);
            List<RetrievalResult> out = new ArrayList<>();
            for (int idx : order) {
                if (idx >= 0 && idx < candidates.size() && out.size() < k) {
                    out.add(candidates.get(idx));
                }
            }
            // 精排结果不全时,按原顺序补足
            for (int i = 0; i < candidates.size() && out.size() < k; i++) {
                if (!order.contains(i)) {
                    out.add(candidates.get(i));
                }
            }
            return out;
        } catch (Exception e) {
            System.err.println("重排失败,保留原召回: " + e.getMessage());
            return candidates.subList(0, Math.min(k, candidates.size()));
        }
    }

    private static double rrf(double rank1, double rank2) {
        double s = 0;
        if (rank1 > 0) {
            s += 1.0 / (RRF_K + rank1);
        }
        if (rank2 > 0) {
            s += 1.0 / (RRF_K + rank2);
        }
        return s;
    }

    private static List<Integer> rankIds(double[] scores) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) {
            ids.add(i);
        }
        ids.sort((a, b) -> Double.compare(scores[b], scores[a]));
        return ids;
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    // —— 向量构建(带缓存,批量 embed) ——

    private List<float[]> buildVectors(Embedder embedder, EmbeddingCache cache) {
        try {
            int n = chunks.size();
            List<float[]> vec = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                vec.add(null);
            }
            List<Integer> missing = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String text = chunks.get(i).text();
                if (cache != null && cache.contains(text)) {
                    vec.set(i, cache.get(text));
                } else {
                    missing.add(i);
                }
            }
            for (int start = 0; start < missing.size(); start += EMBED_BATCH) {
                int end = Math.min(start + EMBED_BATCH, missing.size());
                List<String> batchTexts = new ArrayList<>();
                for (int j = start; j < end; j++) {
                    batchTexts.add(chunks.get(missing.get(j)).text());
                }
                List<float[]> batchVec = embedder.embedBatch(batchTexts);
                for (int j = 0; j < batchVec.size(); j++) {
                    int id = missing.get(start + j);
                    vec.set(id, batchVec.get(j));
                    if (cache != null) {
                        cache.put(chunks.get(id).text(), batchVec.get(j));
                    }
                }
            }
            if (cache != null) {
                cache.save();
            }
            System.err.println("RAG 语义检索: 已向量化 " + n + " 个片段");
            return vec;
        } catch (Exception e) {
            System.err.println("向量化失败,RAG 回退纯 BM25: " + e.getMessage());
            return List.of();
        }
    }

    private static Embedder buildEmbedder() {
        if (!"true".equalsIgnoreCase(Config.get("RAG_EMBEDDING"))) {
            return null;
        }
        try {
            return new EmbeddingClient();
        } catch (Exception e) {
            System.err.println("EmbeddingClient 初始化失败,回退纯 BM25: " + e.getMessage());
            return null;
        }
    }

    private static Reranker buildReranker() {
        if (!"true".equalsIgnoreCase(Config.get("RAG_RERANK"))) {
            return null;
        }
        return new LlmReranker();
    }
}
