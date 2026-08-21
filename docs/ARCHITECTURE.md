# 架构设计

## 一、总架构图

```
用户输入一句话(story.txt)
        │
        ▼
┌─────────────────────────────────────┐
│            Main(总控)               │
│  ① 选模式:短片 / 长片                │
│  ② 调 DeepSeek 修饰提示词            │
│  ③ 用户审查(可修改、可取消)          │
│  ④ 调 Seedance 生成视频              │
└─────────────────────────────────────┘
        │                    │
        ▼                    ▼
   DeepSeek 修饰          Seedance 生成
   (提示词工程)           (文字 → 视频)
        │                    │
        ▼                    ▼
   画面描述              output.mp4 / final.mp4
```

## 二、核心模块

| 类 | 职责 |
|----|------|
| `Main` | 总控:模式选择、流程编排、用户交互 |
| `DeepSeekClient` | 调 DeepSeek,负责"修饰提示词" |
| `VideoClient` | 调 Seedance(火山引擎 Ark),负责"生成视频" |
| `Config` | 读 config.properties,管密钥 |
| `StoryboardAgent` | 长片模式:拆多镜头 + re-plan 质检 |
| `Shot` / `StoryboardParser` | 镜头数据结构 + JSON 解析 |
| `VideoAssembler` | 调 ffmpeg 拼接成片 |

## 三、Agent Skill 框架(核心设计)

一个合格的 AI 动画 agent,应该由这些 **skill** 各司其职地协作。现状如下:

| Skill | 干什么 | 现状 |
|-------|--------|------|
| **提示词工程** | 把模糊想法具体化(场景/氛围/光线/细节) | ✅ 已做(DeepSeek 修饰) |
| **负面提示词** | 排除不想要的(模糊/水印/畸形) | ❌ 待做 |
| **一致性控制** | 多镜头间角色/场景保持统一(参考图/首尾帧) | ❌ 待做(核心难题) |
| **参数调节** | 时长/分辨率/运动强度可调 | ⚠️ 部分(时长可调,其余写死) |
| **质检诊断** | 生成后自动打分(画质/内容/连贯性) | ❌ 待做 |
| **后期处理** | 插帧/超分/局部修补,提升精致度 | ❌ 待做 |
| **剪辑** | 拼接 + 调色 + 配乐 | ⚠️ 只有 ffmpeg 硬拼 |

## 四、两种模式

### 短片模式(一个视频)
```
输入 → DeepSeek 修饰成一段描述 → 用户审查 → Seedance 生成 → output.mp4
```

### 长片模式(多镜头拼成片)
```
输入 → StoryboardAgent 拆多镜头 + re-plan → 用户审查
     → 逐镜头 Seedance 生成 → ffmpeg 拼接 → final.mp4
```

> ⚠️ 长片目前各镜头间可能不连贯(一致性难题,见 ROADMAP)。

## 五、数据流

```
story.txt(文字)
  → DeepSeek.chat() → 画面描述(文本)
  → [用户审查]
  → VideoClient.submit(描述, 时长) → 任务 id
  → VideoClient.waitForVideo() → 视频 URL
  → VideoClient.download() → .mp4 文件
  → (长片) VideoAssembler.concat() → final.mp4
```
