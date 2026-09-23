# story-to-animation-agent

一个 **AI 动画提示词 agent**（数字导演）。支持两种模式：

## 模式一：短片模式（一句话 → 专业提示词）

输入一句想法 → 情节定位 → 世界观 → 6 段式分镜 → 产出 VIDEOPROMPT（可复制到即梦 / 可灵 / Seedance 等使用），可选生成预览视频。预览视频可选首帧和尾帧两张图片；不选尾帧时，维持原来的单首帧模式。

## 模式二：读小说模式（小说 → 多镜头分镜 + 配图）

输入一段小说（超过 100 字自动进入）→ 故事拆解（角色卡 / 世界观 / 情节分段）→ 多镜头分镜 → 每个镜头生成一张关键帧图 + 描述。

```
输入小说
  → ① 故事拆解：角色卡 / 画风 / 世界观 / 情节分段
  → ② 多镜头分镜：每个镜头有 场景/景别/动作/运镜/情绪
  → ③ 每个镜头生成关键帧图 + 一句描述（用途/画面/运镜/情绪）
```

## 核心特性

- **RAG 检索增强**：语料库「文档级 `corpus.json`(50 份打分+标签) → 片段级 `corpus_fragments.json`(800+ 条,带题材/运镜/情绪/画风标签)」，**倒排索引 + BM25 召回** + 可选**向量语义检索** + 可选 **LLM 重排**，分镜设计时自动检索相关范例做 few-shot。
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

小说模式完成分镜后会在 `output/novel-job-<任务编号>.json` 保存任务快照。若视频生成中断，可用该编号从未完成的镜头继续：

```bash
./mvnw compile exec:java -Dexec.args="--resume 任务编号"
```

续跑会复用已有的镜头片段和关键帧；若视频任务已提交但还没下载，会从快照读取任务编号继续查询，不会重复提交同一镜头。

小说模式生成配图后，可在继续视频生成前检查或替换 `output/shot-任务编号-镜头序号.jpg`。视频步骤会直接使用这些图片作为对应镜头的首帧。

结构化创作的温度默认是 `0.65`。若想让分镜更稳定，可在 `config.properties` 中调低 `JSON_TEMPERATURE`；提高数值会增加变化，但不会保证每次都产生完全不同的结果。

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
  ├── Chunk / ChunkStore / TextTokenizer / TextIndex          # RAG 分片/索引/BM25
  ├── Retriever / Embedder / EmbeddingClient / EmbeddingCache # RAG 召回/向量
  ├── Reranker / LlmReranker / RagEval                        # RAG 重排/评测
  └── DeepSeekClient / ImageClient / VideoClient              # 外部服务
prompts/              # 提示词模板（改提示词 = 改 txt）
corpus.json           # 文档级语料库（50 份打分 + 标签的提示词）
corpus_fragments.json # 片段级语料库（800+ 条，带题材/运镜/情绪/画风标签）
output/               # 产出（分镜脚本 / 关键帧图 / 向量缓存）
```

> 语料库的生成脚本（提取 → 清洗打分 → 切分）在上级目录的 `corpus_tools/` 下（Python）。
