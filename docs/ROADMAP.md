# 路线图

## 当前已完成 ✅

- [x] DeepSeek 结构化输出(json_object + 低温 0.3,保证字段稳定)
- [x] 短片模式:定位 → 世界观 → 6 段式分镜 → VIDEOPROMPT
- [x] 读小说模式:拆解 → 多镜头分镜 → 每镜配图
- [x] RAG 检索增强(corpus.json 50 份 + 加权检索 few-shot)
- [x] Agent 骨架(Skill / Agent / Console / Context)
- [x] 一致性铁律(提示词锁定 + 图生图参考链)
- [x] 人工审查闸门(每步可 y/n/r/改,不白花钱)
- [x] 文生图 / 图生视频(Seedream / Seedance)

## 下一步(按优先级)

### 1. 一致性控制 Skill(最难)
把 `docs/SEEDANCE-API.md` 里已查明的 API 真正用起来:
- 首尾帧(`first_frame` + `last_frame`)定义镜头起终点
- 尾帧续拍(`return_last_frame`)让多镜头无缝衔接
- 参考图(`reference_image`)锁定角色,贯穿每个镜头

### 2. 读小说模式出片
多镜头目前只到「图 + 脚本」,补上逐镜头图生视频 + 拼接成片(ffmpeg)。

### 3. 质检诊断 Skill
生成后多模态打分(画质 / 内容 / 连贯性),不合格自动重生成。

### 4. 负面提示词 / 参数调节
负面约束目前由模型生成;时长 / 分辨率已可配,运动强度等仍未放开。

## 长期目标

对标 B 站发布的精致视频,把上面的 skill 逐个打通,让 agent 具备专业视频制作流程的完整能力。

> 原则:一个 skill 一个 skill 地补,每步都能跑、都有产出,不急功近利。
