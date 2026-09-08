package com.example.animation;

import java.util.List;

/**
 * 小说拆解结果:角色卡 + 画风 + 世界观 + 情节分段。
 */
public record NovelBreakdown(String characters, String style, WorldBuilding world, List<String> segments) {}
