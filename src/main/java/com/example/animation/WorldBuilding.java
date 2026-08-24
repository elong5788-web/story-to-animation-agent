package com.example.animation;

/**
 * 世界观(氛围)8 个子维度。
 * 强调维度(色调/光线/尺度/奇观)决定画面;弱化维度(基调/神秘感/气象/文化)只做背景。
 */
public record WorldBuilding(String tone, String scale, String mystery, String wonder,
                            String palette, String lighting, String weather, String culture) {
    /** 强调维度:给画面/8维度生成用,只含色调光线尺度奇观 */
    public String toCoreText() {
        return "色调:" + palette + ",光线:" + lighting + ",尺度:" + scale + ",奇观:" + wonder;
    }

    /** 全部维度:给用户看 */
    public String toText() {
        return "色调:" + palette + ",光线:" + lighting + ",尺度:" + scale + ",奇观:" + wonder
                + ",基调:" + tone + ",神秘感:" + mystery + ",气象:" + weather + ",文化:" + culture;
    }
}
