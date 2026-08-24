package com.example.animation;

/**
 * 情节定位:agent 搜索知识,识别出具体桥段/角色/风格。
 */
public record Localization(String work, String scene, String characters, String plot, String iconicVisual, String style) {
    public String toText() {
        return "作品:" + work + "\n桥段:" + scene + "\n角色:" + characters
                + "\n剧情:" + plot + "\n名场面:" + iconicVisual + "\n图像风格:" + style;
    }
}
