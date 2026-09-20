package com.example.animation;

/**
 * 文本工具:拼 JSON 时的转义、剥大模型输出的代码块外壳等杂活。
 * 之前散落在三个 Client 里的 escape() 合并到这里,避免重复。
 */
public class TextUtil {

    /** 把字符串转成能安全放进 JSON 的文本(转义反斜杠/引号/换行/制表) */
    public static String jsonEscape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /** 剥掉大模型偶尔加在外面的 ```json ... ``` 外壳(json_object 模式也可能偶发,防御性处理) */
    public static String stripCodeFence(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int newline = t.indexOf('\n');
            t = newline >= 0 ? t.substring(newline + 1) : t.substring(3);
        }
        int lastFence = t.lastIndexOf("```");
        if (lastFence >= 0) {
            t = t.substring(0, lastFence);
        }
        return t.trim();
    }
}
