# story-to-animation-agent

一个 **AI 动画提示词 agent**（数字导演）。支持两种模式：

## 模式一：短片模式（一句话 → 专业提示词）

输入一句想法 → 情节定位 → 世界观 → 6 段式分镜 → 产出 VIDEOPROMPT（可复制到即梦 / 可灵 / Seedance 等使用），可选生成预览视频。

## 模式二：读小说模式（小说 → 多镜头分镜 + 配图）

输入一段小说（超过 100 字自动进入）→ 故事拆解（角色卡 / 世界观 / 情节分段）→ 多镜头分镜 → 每个镜头生成一张关键帧图 + 描述。

```
输入小说
  → ① 故事拆解：角色卡 / 画风 / 世界观 / 情节分段
  → ② 多镜头分镜：每个镜头有 场景/景别/动作/运镜/情绪
  → ③ 每个镜头生成关键帧图 + 一句描述（用途/画面/运镜/情绪）
```

## 核心特性

- **RAG 检索增强**：内置 50 份高质量提示词语料库（`corpus.json`，已打分 + 打标签），分镜设计时自动检索相关范例做 few-shot。
- **Agent 架构**：`Skill` 抽象 + `Agent` 编排 + `Console` 解耦 + `Context` 状态，能力可插拔、可扩展。
- **一致性铁律**：角色 / 风格 / 氛围全片锁定，禁止换人改风格。
- **电影级提示词**：FACS 面部编码（AU 码）、运镜手法清单、电影摄影参数。

## 技术栈

- Java 17 + Maven（mvnw wrapper）
- Jackson（JSON）
- DeepSeek（文本大脑：定位 / 世界观 / 分镜 / 小说拆解）
- 火山引擎 Ark：Seedream（文生图）、Seedance（视频生成，可选）

## 怎么运行

1. 在 `config.properties` 里填两个密钥（已 gitignore）：
   - `DEEPSEEK_API_KEY`（DeepSeek）
   - `ARK_API_KEY`（火山引擎 Ark）
2. 运行：

```bash
./mvnw compile exec:java
```

3. 输入一句话（走短片模式）或一段小说（超过 100 字走读小说模式），一路审查产出。

## 测试

```bash
./mvnw test
```

覆盖：检索加权（核心词权重 > 宽泛词）、关键帧/动作提示词拼接。

## 目录结构

```
src/main/java/com/example/animation/
  ├── Skill.java / Agent.java / Console.java / Context.java   # agent 骨架
  ├── Localizer / WorldBuilder / ShotDesigner                 # 短片模式三步
  ├── NovelParser / StoryboardDesigner                        # 读小说模式
  ├── ShotImageGenerator / StoryboardWriter                   # 配图 + 产出
  ├── Retriever / CorpusEntry                                 # RAG 检索
  └── DeepSeekClient / ImageClient / VideoClient              # 外部服务
prompts/      # 提示词模板（改提示词 = 改 txt）
corpus.json   # RAG 语料库（50 份打分 + 标签的提示词）
output/       # 产出（分镜脚本 / 关键帧图）
```

> 语料库的生成脚本（提取 → 清洗打分 → 切分）在上级目录的 `corpus_tools/` 下（Python）。
