# 架构设计

## 一、总架构

```
用户输入(一句话 / 一段小说)
        │
        ▼
      Main(总控:选模式 + 组装 skill 流水线 + 启动)
        │
        ├─ 短片模式:Localizer → WorldBuilder → ShotDesigner → ScriptWriter ─┐
        │                                                    └→ 可选 VideoGenerator(预览短片)
        └─ 读小说模式:NovelParser → StoryboardDesigner → StoryboardWriter ─┘
                                                       └→ 可选 ShotImageGenerator(每镜配图)
        │
        ▼
   外部服务:DeepSeekClient(文本) / ImageClient(Seedream 图) / VideoClient(Seedance 视频)
```

## 二、Agent 骨架(核心设计)

一个合格的 AI 动画 agent,由若干 **skill** 各司其职地协作:

| 类 | 职责 |
|----|------|
| `Skill<T>` | 一个能力的抽象基类。模板方法 `execute()` = 生成(带 3 次重试)→ 人工审查(y/n/r/改)→ 写回 `Context` |
| `Agent` | 编排器:按注册顺序跑 skill 列表,遇取消即停。加减能力只改 `Main` 里的流水线列表 |
| `Context` | 流水线累积状态(input / loc / world / design / novelBreakdown / storyBoard / cancelled),封装读写 |
| `Console` | I/O 抽象,命令行用 `SystemConsole`;换 Web / 无头自动化只需换一个实现 |

`Skill` 子类只实现 5 个钩子:`generateOnce` / `show` / `alternateHint` / `feedbackText` / `store`,审查循环、重试、失败降级由基类统一处理。

## 三、短片模式(一句话 → VIDEOPROMPT)

| Skill | 职责 |
|-------|------|
| `Localizer` | 情节定位:作品 / 桥段 / 角色卡 / 画风(8 种选一)。角色卡是锁一致性的根基 |
| `WorldBuilder` | 8 维世界观氛围(强调:色调 / 光线 / 尺度 / 奇观;弱化:基调 / 神秘 / 气象 / 文化) |
| `ShotDesigner` | 6 段式 VIDEOPROMPT(角色 / 场景 / 画风 / 画质 / 时间轴 / 声音 / 负面),注入 RAG few-shot |
| `ScriptWriter` | 渲染成专业 VIDEOPROMPT 脚本存盘 |
| `VideoGenerator` | 可选:关键帧(用户图 / AI 文生图)→ 图生视频 |

## 四、读小说模式(小说 → 多镜头分镜 + 配图)

| Skill | 职责 |
|-------|------|
| `NovelParser` | 角色卡 + 画风 + 世界观 + 情节分段(5~12 段);角色卡为空判失败重试 |
| `StoryboardDesigner` | 每段一个镜头 `Shot`(场景 / 景别 / 动作 / 运镜 / 情绪 AU 码 / 画质) |
| `StoryboardWriter` | 多镜头脚本存盘 |
| `ShotImageGenerator` | 每镜关键帧图:首镜文生图,后续图生图以前一镜为参考锁定一致性 |

## 五、RAG 检索增强(分片 → 索引 → 召回 → 重排 → 评测)

```
corpus.json(文档级,50 份打分+标签)
  → corpus_tools/fragment_corpus.py 切成 corpus_fragments.json(片段级,800+ 条,带 genre/camera/emotion/style)
  → ChunkStore 加载 → TextIndex 建倒排索引(中文字符 bigram)
  → Retriever 召回:BM25(正文 + 标签加权 + 质量分先验)+ 可选向量语义(RRF 融合)+ 可选 LLM 重排
```

| 环节 | 实现 |
|------|------|
| 分片 | `corpus_fragments.json`(DeepSeek 切分,40~300 字自洽片段,带题材/运镜/情绪/画风标签) |
| 索引 | `TextIndex` 倒排索引,O(命中词) 而非 O(N) 线性扫 |
| 召回 | `Retriever`:BM25 + 可选向量语义(RRF 倒数排名融合) |
| 重排 | `LlmReranker`(可选,DeepSeek 对 top-N 精排) |
| 评测 | `RagEval`(precision@k / MRR,以 genre 标签为真值) |

开关(`config.properties`):`RAG_EMBEDDING`(向量 hybrid)、`RAG_RERANK`(重排)。任一失败自动降级纯 BM25。

> 实测注记:在 761 片段规模下,多模态 embedding(纯文本输入)会让 precision@3 从 0.60 降到 0.47(语义相近但题材跑偏的「通用」方法论片段被捞上来),故 `RAG_EMBEDDING` 默认关。语料规模变大或换专用文本 embedding 模型后再开。

## 六、一致性铁律

角色 / 画风 / 氛围作为「已锁定常量」在提示词中明文锁定(见 `prompts/expand.txt`、`storyboard.txt`),代码层靠「首镜文生图定基准 → 后续图生图链式引用前一镜」兜底。

## 七、数据流(视频生成)

```
story.txt(文字)
  → DeepSeekClient.chatJson() → 结构化结果(JSON,json_object + 低温 0.3)
  → [人工审查]
  → ImageClient.textToImage() → 关键帧图(可选)
  → VideoClient.submitImageToVideo() → task id
  → VideoClient.waitForVideo() → 视频 URL
  → VideoClient.download() → .mp4
```
