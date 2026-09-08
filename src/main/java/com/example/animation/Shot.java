package com.example.animation;

/**
 * 一个镜头:对应小说的一段情节。
 */
public record Shot(String time, String scene, String framing, String action, String camera, String emotion, String quality) {}
