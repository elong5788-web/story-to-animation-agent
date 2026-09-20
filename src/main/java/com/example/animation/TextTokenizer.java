package com.example.animation;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文分词:字符 bigram + 单字,英文按整词(小写)。
 * CJK bigram 是搜索引擎对无词典中文的标准做法,零第三方依赖。
 * 单字也入索引,是为了让"推/拉"这类单字运镜词、以及单字查询能命中。
 */
public final class TextTokenizer {

    private TextTokenizer() {}

    /** 把一段文本切成检索词元 */
    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        StringBuilder cjk = new StringBuilder();
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isHan(c)) {
                flushAscii(ascii, tokens);
                cjk.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                flushCjk(cjk, tokens);
                ascii.append(c);
            } else {
                flushCjk(cjk, tokens);
                flushAscii(ascii, tokens);
            }
        }
        flushCjk(cjk, tokens);
        flushAscii(ascii, tokens);
        return tokens;
    }

    private static boolean isHan(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }

    /** 中文字串:单字直接入索引;多字出 bigram + 单字 */
    private static void flushCjk(StringBuilder cjk, List<String> tokens) {
        String s = cjk.toString();
        if (s.length() == 1) {
            tokens.add(s);
        } else if (s.length() >= 2) {
            for (int i = 0; i + 1 < s.length(); i++) {
                tokens.add(s.substring(i, i + 2));
            }
            for (int i = 0; i < s.length(); i++) {
                tokens.add(s.substring(i, i + 1));
            }
        }
        cjk.setLength(0);
    }

    /** 英文/数字串:整词小写入索引 */
    private static void flushAscii(StringBuilder ascii, List<String> tokens) {
        String s = ascii.toString();
        if (!s.isEmpty()) {
            tokens.add(s.toLowerCase());
        }
        ascii.setLength(0);
    }
}
