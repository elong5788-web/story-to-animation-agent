package com.example.animation;

/**
 * 一个分镜镜头。
 * 字段名和大模型返回的 JSON 字段一一对应(shot / shotType / description / action / duration)。
 * duration = 该镜头时长(秒)。
 */
public record Shot(int shot, String shotType, String description, String action, int duration) {
}
