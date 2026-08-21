# Seedance API 参考(已确认的接口格式)

> 抓取时间:2026-08-21。来源:官方/社区 API 参考文档。

## Base URL
```
https://ark.cn-beijing.volces.com/api/v3/contents/generations/tasks
```

## 四种模式(content 数组的写法)

### ① 文生视频
```json
{
  "model": "doubao-seedance-2-0-fast-260128",
  "content": [{"type": "text", "text": "画面描述"}],
  "ratio": "16:9", "duration": 5, "resolution": "720p"
}
```

### ② 图生视频(首帧)
```json
"content": [
  {"type": "text", "text": "动作描述"},
  {"type": "image_url", "image_url": {"url": "图片URL"}, "role": "first_frame"}
]
```

### ③ 首尾帧
```json
"content": [
  {"type": "text", "text": "过渡动作描述"},
  {"type": "image_url", "image_url": {"url": "首帧URL"}, "role": "first_frame"},
  {"type": "image_url", "image_url": {"url": "尾帧URL"}, "role": "last_frame"}
]
```

### ④ 参考图(锁定角色一致性)
```json
"content": [
  {"type": "text", "text": "[图1]的人物在跳舞"},
  {"type": "image_url", "image_url": {"url": "参考图URL"}, "role": "reference_image"}
]
```
> ⚠️ 参考图模式需用特定模型 `doubao-seedance-1-0-lite-i2v-250428`。

## 关键机制

- **尾帧续拍**:第一段加 `"return_last_frame": true`,完成后拿到 `last_frame_url`,作为下一段的 `first_frame`,实现无缝衔接。
- **本地图片**:转 base64 后拼成 `data:image/png;base64,xxx` 传入。
- **草稿预览(省钱)**:`"draft": true, "resolution": "480p"` 先生成便宜预览,满意再生成正式版。

## 首尾帧提示词模板(保证衔接质量)

```
@图片1 为首帧。@图片2 为尾帧。
保持同一主体、服装、形状和场景逻辑。
只生成两帧之间的连续动作。
```

## 常见失败与修复

| 失败 | 修复 |
|------|------|
| 主体变形 | 只锁定关键特征,别加多余风格变化 |
| 跳切 | 加"连续过渡" + 单一动作路径 |
| 镜头乱晃 | 锁死机位或只做一次缓慢推近 |
