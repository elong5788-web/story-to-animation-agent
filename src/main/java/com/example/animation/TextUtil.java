package com.example.animation;

/**
 * 文本工具:处理大模型返回内容时的一些杂活。
 */
public class TextUtil {

    /** 剥掉大模型喜欢加在外面的 ```json ... ``` 外壳 */
    public static String stripCodeFence(String text) {
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
