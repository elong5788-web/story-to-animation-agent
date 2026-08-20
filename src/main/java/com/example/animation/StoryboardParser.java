package com.example.animation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 把大模型返回的文本(可能带 ```json 外壳)解析成镜头列表。
 */
public class StoryboardParser {

    private final ObjectMapper mapper = new ObjectMapper();

    /** 从大模型回复里提取并解析出镜头列表 */
    public List<Shot> parse(String modelReply) throws Exception {
        String json = stripCodeFence(modelReply);
        return mapper.readValue(json, new TypeReference<List<Shot>>() {
        });
    }

    /** 剥掉大模型喜欢加在外面的 ```json ... ``` 外壳 */
    static String stripCodeFence(String text) {
        String t = text.trim();

        // 去掉开头的 ```json 或 ```
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline >= 0) {
                t = t.substring(firstNewline + 1);
            }
        }

        // 去掉结尾的 ```
        int lastFence = t.lastIndexOf("```");
        if (lastFence >= 0) {
            t = t.substring(0, lastFence);
        }

        return t.trim();
    }
}
