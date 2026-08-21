# story-to-animation-agent

一个 **AI 动画 Agent**:输入一句话想法,自动完成「提示词修饰 → 用户审查 → 视频生成」,把文字变成视频。

## 它能做什么

- **短片模式**:一句话 → DeepSeek 修饰成画面描述 → 生成 1 个视频
- **长片模式**:一句话 → 拆成多镜头 → 逐镜头生成 → ffmpeg 拼成一条短片
- **用户审查**:生成前先给你看描述,你可修改、可取消,满意了才花视频的钱

## 核心分工

| 角色 | 模型/工具 | 干什么 |
|------|-----------|--------|
| 大脑 | DeepSeek | 把模糊的一句话修饰成具体画面描述 |
| 画笔 | Seedance(火山引擎) | 把画面描述变成视频 |
| 剪辑 | ffmpeg | 把多个镜头拼成片 |

## 技术栈

- Java 17 + Maven
- Jackson(JSON 解析)
- DeepSeek API、火山引擎 Ark(Seedance)API

## 怎么运行

1. 在 `config.properties` 里填两个密钥(已 gitignore,不会上传)
2. 在 `story.txt` 里写你想生成的画面描述
3. 运行:

```bash
./mvnw -q compile exec:java -Dexec.mainClass=com.example.animation.Main
```

## 文档

- [架构设计](docs/ARCHITECTURE.md)
- [路线图](docs/ROADMAP.md)
