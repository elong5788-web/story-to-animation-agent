package com.example.animation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 提示词加载器:从 prompts/ 目录读取提示词。
 * 改提示词 = 改 txt 文件,不用改代码、不用重新编译。
 */
public class Prompts {

    public static String localize() {
        return read("prompts/localize.txt");
    }

    public static String world() {
        return read("prompts/world.txt");
    }

    public static String expand() {
        return read("prompts/expand.txt");
    }

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取提示词文件失败: " + path, e);
        }
    }
}
