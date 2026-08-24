# story-to-animation-agent

一个 **AI 视频提示词工程 agent**(数字导演):输入一句想法,自动完成「情节定位 → 世界观 → 6 段式分镜设计 → 用户审查」,产出一份**专业级 VIDEOPROMPT**,可直接复制到 cineART / 即梦 / 可灵 / Seedance 等工具生成视频。

> 重点:它的核心产出是**提示词和分镜脚本**,不是视频本身。视频生成只是可选的"预览短片"。

## 核心流程(6 步,每步都可人工审查)

```
输入想法
  → ① 情节定位(识别作品/桥段/角色/风格)
  → ② 世界观(氛围:色调/光线/尺度/奇观)
  → ③ 6 段式分镜设计(角色/场景/画风画质/时间轴/声音/限制)
  → ④ 产出 VIDEOPROMPT(打印 + 存 txt)
  → ⑤ 可选:生成预览短片(默认 5 秒)
```

每一步都是「AI 生成 → 你审查 → 不满意就改 → 满意才继续」(human-in-the-loop)。

## 产出的 VIDEOPROMPT 结构(6 段式)

对齐 AIGC 教程的专业写法:

```
【基础设定】角色(完整外貌+服装)+ 场景
【氛围与画质】画风 + 画质(胶片/镜头/布光/色彩)+ 氛围
【画面内容】timeline 时间分段(每段:时间/景别/动作/运镜/情绪)
【声音】同期声/环境音
【限制】负面约束(no text、no watermark、避免塑料感…)
```

核心亮点:

- **一致性铁律**:锁定风格/角色/氛围,全片一套,禁止换人改风格
- **电影摄影参数**:35mm 胶片、Kodak Vision3、ARRI Alexa、Cooke 镜头、伦勃朗布光、LogC4、4300K 色温…
- **FACS 面部编码(AU 码)**:AU1 内眉上抬、AU4 眉毛下压、AU7 眼睑收紧…,情绪用肌肉指令不写形容词
- **运镜手法清单**:低机位匀速推进、弧形环绕、FPV 穿越、8mm 鱼眼、康斯坦丁运镜、杜琪峰站位…

## 技术栈

- Java 17 + Maven(mvnw wrapper)
- Jackson(JSON 解析)
- DeepSeek API(文本大脑:定位/世界观/分镜设计)
- 火山引擎 Ark:Seedream(文生图)、Seedance(视频生成,可选)

## 架构(15 个类,职责清晰)

```
Main(纯编排) → Localizer / WorldBuilder / ShotDesigner / ScriptWriter / VideoGenerator
             → DeepSeekClient / ImageClient / VideoClient(外部服务)
             → Localization / WorldBuilding / ShotDesign(数据模型)
             → Config / Prompts / InputHandler / TextUtil(基础设施)
```

提示词放在 `prompts/*.txt`,改提示词 = 改 txt 文件,不用改代码。

## 怎么运行

1. 在 `config.properties` 里填两个密钥(已 gitignore,不会上传):
   - `DEEPSEEK_API_KEY`(DeepSeek)
   - `ARK_API_KEY`(火山引擎 Ark)
2. 运行:

```bash
./mvnw -q compile exec:java -Dexec.mainClass=com.example.animation.Main
```

3. 输入你的想法(直接打字 / 粘贴小说片段 / 输 txt 文件路径),一路审查,最后得到 VIDEOPROMPT。

## 文档

- [架构设计](docs/ARCHITECTURE.md)
- [路线图](docs/ROADMAP.md)
- [Seedance API 参考](docs/SEEDANCE-API.md)
