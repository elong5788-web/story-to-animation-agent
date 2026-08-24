package com.example.animation;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

/**
 * 输入处理:读取用户输入(粘贴/文件/默认),以及时间戳、路径转换等杂活。
 */
public class InputHandler {

    /** 本次运行的时间戳 */
    public static String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    /** 读多行文字,直到空行(用于粘贴长文字/小说片段) */
    public static String readRest(Scanner sc) {
        StringBuilder sb = new StringBuilder();
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            if (line.isBlank()) break;
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    /** 解析输入:空则用默认;是文件路径则读文件;否则当粘贴文字 */
    public static String resolveInput(String typed, String fromFile) throws Exception {
        if (typed.isBlank()) return fromFile;
        if (!typed.contains("\n")) {
            try {
                Path p = Path.of(normalizePath(typed));
                if (Files.exists(p) && Files.isRegularFile(p)) {
                    System.out.println("(已读取文件: " + p + ")");
                    return readTextFile(p);
                }
            } catch (Exception ignored) {
            }
        }
        Files.writeString(Path.of("story.txt"), typed, StandardCharsets.UTF_8);
        return typed;
    }

    /** 把 Git Bash 风格路径 /c/Users/... 转成 Windows 的 C:/Users/... */
    public static String normalizePath(String s) {
        if (s.length() >= 3 && s.charAt(0) == '/' && s.charAt(2) == '/') {
            return Character.toUpperCase(s.charAt(1)) + ":" + s.substring(2);
        }
        return s;
    }

    /** 读文本文件:自动识别 UTF-8/GBK,太长截断到前 3000 字 */
    public static String readTextFile(Path p) throws Exception {
        byte[] bytes = Files.readAllBytes(p);
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.contains("\uFFFD")) {
            content = new String(bytes, Charset.forName("GBK"));
        }
        content = content.trim();
        if (content.length() > 3000) {
            System.out.println("(文件太大,只取前 3000 字)");
            content = content.substring(0, 3000);
        }
        return content;
    }

    /** 读 story.txt 默认故事 */
    public static String readStory(String[] args) throws Exception {
        Path storyFile = Path.of("story.txt");
        if (Files.exists(storyFile)) {
            return Files.readString(storyFile, StandardCharsets.UTF_8).trim();
        }
        if (args.length > 0) {
            return String.join(" ", args);
        }
        return "一个女孩在雨天撑伞走过街道";
    }
}
