package com.example.animation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 用 DeepSeek 做检索精排:把召回 top-N 的候选片段连同 query 一起喂给模型,
 * 让它按相关度重新排序,只输出下标顺序。
 */
public class LlmReranker implements Reranker {

    private static final ObjectMapper mapper = new ObjectMapper();
    private final DeepSeekClient ds = new DeepSeekClient();

    @Override
    public List<Integer> rerank(String query, List<Chunk> candidates) throws Exception {
        String system = """
                你是检索结果精排器。给定查询和若干候选片段,按与查询的相关度从高到低排序。
                只输出 JSON:{"order":[下标,...]},下标从 0 开始,是候选片段在原列表里的位置,必须覆盖所有候选、不重复。""";
        StringBuilder user = new StringBuilder("查询: ").append(query).append("\n\n候选片段:\n");
        for (int i = 0; i < candidates.size(); i++) {
            user.append(i).append(". ").append(candidates.get(i).text()).append("\n");
        }
        String reply = ds.chatJson(system, user.toString());
        JsonNode n = mapper.readTree(reply);
        List<Integer> order = new ArrayList<>();
        for (JsonNode idx : n.path("order")) {
            order.add(idx.asInt());
        }
        return order;
    }
}
