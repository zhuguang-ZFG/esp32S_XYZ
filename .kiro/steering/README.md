---
name: steering-readme
description: esp32S_XYZ Kiro steering 文件索引与加载说明
type: steering
priority: P0
inclusion: auto
---

# Kiro Steering 文件索引

本目录保存 Kiro 在 `esp32S_XYZ` 仓库内执行任务时必须自动加载的项目规程。

## 当前文件（15 个）

### 根约束（优先加载）

| 文件 | 作用 |
|------|------|
| `superpowers.md` | Superpowers 根约束：五步顺序、六条纪律、过半换窗、Planning with Files、三击协议、文档同步强制 |

### 流程规范类

| 文件 | 作用 |
|------|------|
| `spc-planning.md` | SPC 执行规划：Spec→Plan→Code→Verify、M0g 状态、边界红线、证据规则、验证门禁 |
| `code-doc-sync.md` | 编码与文档同步：触发节点、更新清单、逆向禁止 |
| `code-review.md` | 代码审查：架构边界、安全裁决、协议合规、任务模型四维审查 |
| `milestone-acceptance.md` | 里程碑验收：M0~M6 检查清单、验收报告模板 |

### 实现类 Skill

| 文件 | 作用 |
|------|------|
| `protocol-design.md` | 协议设计：四边界的 Schema 先于实现、字段演进规则、错误码管理 |
| `safety-validation.md` | 安全裁决：双重安全校验、STOP vs ESTOP 硬约束、限位/急停独立守卫 |
| `projection-pipeline.md` | 投影管线：write_text/draw 四阶转换、离散化精度、尺寸自适应 |
| `voice-intent.md` | 语音意图：四道关过滤、白名单分流、声纹模式、TTS 播报矩阵 |

### 方法论 Skill

| 文件 | 作用 |
|------|------|
| `tdd.md` | TDD：RED→GREEN→REFACTOR 循环、测试命名、禁止先写实现再补测试 |
| `fake-environment.md` | Fake 环境：fake U1/DeviceServer/AI 的启动、注入、验收 |
| `code-simplifier.md` | 代码简化：5 种模式、禁止跨里程碑重构 |
| `ui-ux-pro-max.md` | UI/UX：模板优先、不手搓组件、三页制、状态/加载/错误全覆盖 |
| `skill-creator.md` | Skill 创建：6 要素模板、6 步创建、禁止宽度泛滥 |

### 规范类

| 文件 | 作用 |
|------|------|
| `chinese-communication.md` | 中文交流：全程中文、术语规范、文档/注释/commit 语言规则 |

## 权威文档顺序

Kiro 执行任何任务前必须按顺序读取：

1. `docs/接续指令.md`
2. `docs/架构定稿-v2.md`
3. `docs/实施计划-v2.md`
4. `docs/全局规划-Planning-with-Files.md`
5. `docs/M0-进度报告.md`
6. 与任务相关的专项文档，例如 `docs/硬件核对报告.md`、`docs/编码任务索引-v2.md`、`docs/schemas/README.md`

若 steering 与 `docs/` 冲突，以 `docs/` 为准，并先修正文档或报告冲突。

## 当前项目状态摘要

- 当前阶段：`M0 设计期验证`。
- 当前接续：`M0g 硬件核对报告`。
- 已完成：M0g 轮 1 ESP32-S3/GPIO 核对；M0g 轮 2 HR4988E 核对。
- 下一步：M0g 轮 3 ES8311 / ES7210 核对。
- M0f 实物上电抽测仍等待实物，不阻塞 M1~M3 fake 轨，但阻塞真实硬件联调承诺。

## Kiro 执行硬约束

- 必须使用中文沟通。
- 必须先验证前置条件，再实现。
- 必须按 Spec -> Plan -> Code -> Verify 工作。
- 必须确认真实文件路径、函数、schema、硬件证据后再修改。
- 必须遵守 Edge-A/B/C/D 分层边界。
- 必须保持代码与文档同步。
- 必须运行相关验证；不能运行时标注原因和替代证据。
- 必须只提交当前任务文件。
- 必须保护用户未要求提交的工作区改动。

## 修订记录

- 2026-05-15：重写为 UTF-8 中文；移除不存在 steering 文件的误导引用；对齐当前 M0g 轮 3 接续状态。
