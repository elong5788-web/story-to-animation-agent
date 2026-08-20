package com.example.animation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 读取配置(API 密钥)。
 * 优先级:config.properties 文件 > 环境变量。
 * 这样密钥只需在 config.properties 里填一次,以后运行不用每次 export。
 */
public class Config {

    private static final Properties props = load();

    private static Properties load() {
        Properties p = new Properties();
        Path file = Path.of("config.properties");
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException e) {
                System.err.println("读取 config.properties 失败,回退到环境变量: " + e.getMessage());
            }
        }
        return p;
    }

    /** 先看配置文件,再看环境变量;都没有就返回 null */
    public static String get(String key) {
        String v = props.getProperty(key);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        return System.getenv(key);
    }
}
