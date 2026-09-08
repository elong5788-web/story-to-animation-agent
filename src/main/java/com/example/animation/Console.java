package com.example.animation;

/**
 * 交互接口:把 agent 的输入输出和控制台解耦。
 * 命令行用 SystemConsole;将来换 Web/API、或做自动化(无人审查),只需换一个实现,agent 核心不动。
 */
public interface Console {
    void print(String s);
    void println(String s);
    String readLine();

    /** 读多行直到空行(粘贴长文字用)。 */
    default String readRest() {
        StringBuilder sb = new StringBuilder();
        while (true) {
            String line = readLine();
            if (line == null || line.isBlank()) break;
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }
}
