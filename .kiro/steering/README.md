# Steering Files - 全局规范与技能库

本目录包含 esp32S_XYZ 项目的全局规范文件，所有开发工作都必须遵循这些规范。

## 核心原则

所有 steering 文件都严格遵循：
- **Superpowers 原则**（`.cursor/rules/superpowers-and-context.mdc`）
- **架构定稿 v2**（`docs/架构定稿-v2.md`）
- **实施计划 v2**（`docs/实施计划-v2.md`）

## 文件清单

### 0. 中文交流规范 (`chinese-communication.md`) ✅

**用途**: 全局中文交流规范

**适用场景**:
- 所有与用户的对话
- 所有文档编写
- 所有提示信息

**核心约束**:
- ✅ 全程使用中文与用户交流
- ✅ 技术术语可保留英文但需注明
- ✅ 代码注释推荐中文
- ❌ 禁止中英文混杂的句子

**关键章节**:
- 交流风格（简洁明了、专业准确）
- 技术术语处理（常用术语对照表）
- 文档编写规范
- 交互场景示例

### 1. UI/UX Pro Max (`ui-ux-pro-max.md`)

**用途**：全局用户体验与界面设计规范

**适用场景**：
- M2.8 小程序最小可用页面
- M3+ 扩展页面开发
- 所有涉及小程序 UI 的工作

**核心约束**：
- ✅ 必须基于 GitHub 成熟模板（uni-app + mp-weixin）
- ✅ 必须在 `docs/ui-template.md` 登记模板来源
- ❌ 禁止手搓页面骨架、组件库、主题
- ❌ 禁止直连 DeviceServer 或设备

**关键章节**：
- 小程序定位：身份/订阅中继（§2bis）
- 页面结构规范（最小可用页面 + 扩展页面）
- 实时订阅（Edge-A WSS）
- 状态展示规范（设备状态 + 任务五态）

### 2. Code Review (`code-review.md`)

**用途**：全局代码审查规范

**适用场景**：
- 所有 PR 审查
- 里程碑验收（M0-M6）
- 代码合并前检查

**核心约束**：
- ✅ 角色职责正确（§2 五角色表）
- ✅ 分层正确，无跨层直连（§3.2）
- ✅ 双重安全裁决生效（§10bis）
- ❌ 禁止绕过安全校验
- ❌ 禁止混用 STOP 与 ESTOP 语义

**关键章节**：
- 架构边界审查（角色职责 + 分层 + 通信边界）
- 安全审查（双重裁决 + 安全校验清单）
- 协议与字段演进审查（schema 先于实现）
- 任务模型审查（任务五态 + STOP vs ESTOP）
- 里程碑审查（M0-M6 完成判定）

### 3. Code Simplifier (`code-simplifier.md`)

**用途**：代码简化与重构规范

**适用场景**：
- M1.7 删除 U8 G-code 回退路径
- M1.3 简化 alarm_code / error_code
- 所有涉及代码重构的工作

**核心约束**：
- ✅ 只改当前子任务明确列出的文件
- ✅ 重构前必须有测试覆盖
- ❌ 禁止"顺手"重构无关代码
- ❌ 禁止跨里程碑重构
- ❌ 禁止无测试覆盖的重构

**关键章节**：
- 简化原则（最小改动 + 删除代码 + 抽象提取 + 重命名）
- 简化模式（5 种：删除死代码、简化条件、提取常量、合并重复、简化嵌套）
- 重构检查清单（重构前 + 重构中 + 重构后）
- 实际示例（M1.7 + M1.3）

### 4. Skill Creator (`skill-creator.md`)

**用途**：技能创建与管理规范

**适用场景**：
- 创建新的 skill 文件
- 管理现有 skill
- 识别需要 skill 的场景

**核心约束**：
- ✅ 流程类 skill 优先于实现类
- ✅ 每个 skill 必须有清晰的使用场景与步骤
- ❌ 禁止创建过于宽泛的 skill
- ❌ 禁止创建与架构定稿 v2 冲突的 skill

**关键章节**：
- Skill 分类（流程类 / 实现类 / 工具类）
- Skill 创建流程（6 步）
- Skill 模板（完整模板）
- 推荐创建的 Skill（7 个）

## 推荐创建的额外 Skill

根据实施计划 v2，建议创建以下 skill：

### 1. 协议设计 Skill (`protocol-design.md`) ✅
- **用途**：指导四条边界的协议设计
- **场景**：M0a 契约固化、M1.1 Edge-D 样例集、M2.3 Edge-B、M2.7 Edge-A
- **核心步骤**：先 schema → 再示例 → 再实现 → 最后测试

### 2. 安全裁决 Skill (`safety-validation.md`) ✅
- **用途**：指导安全裁决的实现
- **场景**：M3.5 安全裁决前置、M3.8 draw_generated 约束校验
- **核心步骤**：识别风险 → 定义双重裁决 → 实现前置裁决 → 实现最终裁决 → 编写测试

### 3. 投影管线 Skill (`projection-pipeline.md`) ✅
- **用途**：指导 write_text / draw_generated 投影管线
- **场景**：M3.3 字体引擎、M3.4 投影管线四阶、M3.8 生成约束
- **核心步骤**：定义输入 → layout_resolver → discretize → safety_validator → 测试

### 4. 语音意图 Skill (`voice-intent.md`) ✅
- **用途**：指导语音意图的处理
- **场景**：M4.3 intent 分流、M4.4 语音触发任务、M4.10 语音 demo
- **核心步骤**：定义 chat vs device → 白名单触发 → intent_submit → TTS 播报 → 测试

### 5. 测试驱动开发 Skill (`tdd.md`) ✅
- **用途**：指导测试驱动开发
- **场景**：所有新功能开发、所有 bug 修复
- **核心步骤**：先写失败测试 → 实现最小代码 → 重构 → 重复

### 6. fake 环境 Skill (`fake-environment.md`) ✅
- **用途**：指导 fake 环境的使用
- **场景**：M0c fake U1/DeviceServer/AI、M1~M3 对着 fake 开发
- **核心步骤**：启动 fake → 配置连接 → 注入错误 → 验证行为

### 7. 里程碑验收 Skill (`milestone-acceptance.md`) ✅
- **用途**：指导里程碑验收
- **场景**：每个 M 结束后
- **核心步骤**：检查完成判定 → 跑回归测试 → 更新文档 → 同步 GitHub

## 使用方法

### 方式 1：自动包含（推荐）

所有 steering 文件都设置为 `inclusion: auto`，会自动加载到 Kiro 的上下文中。

### 方式 2：手动阅读

```bash
# 查看 UI/UX 规范
cat .kiro/steering/ui-ux-pro-max.md

# 查看代码审查规范
cat .kiro/steering/code-review.md

# 查看代码简化规范
cat .kiro/steering/code-simplifier.md

# 查看技能创建规范
cat .kiro/steering/skill-creator.md
```

### 方式 3：通过 Kiro 命令（如果支持）

```
@skill ui-ux-pro-max
@skill code-review
@skill code-simplifier
@skill skill-creator
```

## 文件组织

```
.kiro/steering/
├── README.md                    # 本文件
├── chinese-communication.md     # 中文交流规范 ✅
├── ui-ux-pro-max.md             # UI/UX 规范 ✅
├── code-review.md               # 代码审查规范 ✅
├── code-simplifier.md           # 代码简化规范 ✅
├── skill-creator.md             # 技能创建规范 ✅
├── protocol-design.md           # 协议设计 Skill ✅
├── safety-validation.md         # 安全裁决 Skill ✅
├── projection-pipeline.md       # 投影管线 Skill ✅
├── voice-intent.md              # 语音意图 Skill ✅
├── tdd.md                       # 测试驱动开发 Skill ✅
├── fake-environment.md          # fake 环境 Skill ✅
└── milestone-acceptance.md      # 里程碑验收 Skill ✅
```

## 版本管理

- 所有 steering 文件纳入 Git 版本控制
- 每次更新时添加修订记录
- 与架构定稿 v2 / 实施计划 v2 保持同步

## 质量标准

每个 steering 文件必须：
- [ ] 有清晰的用途说明
- [ ] 有明确的适用场景
- [ ] 有具体的核心约束
- [ ] 有完整的检查清单或步骤
- [ ] 有实际示例或代码片段
- [ ] 与架构定稿 v2 保持一致
- [ ] 与实施计划 v2 保持一致

## 参考文档

- **Superpowers 原则**：`.cursor/rules/superpowers-and-context.mdc`
- **架构定稿 v2**：`docs/架构定稿-v2.md`
- **实施计划 v2**：`docs/实施计划-v2.md`
- **编码任务索引 v2**：`docs/编码任务索引-v2.md`
- **JSON Schema**：`docs/schemas/`

## 维护指南

### 何时更新 steering 文件

- 架构定稿 v2 有重大变更
- 实施计划 v2 有重大调整
- 发现现有规范有遗漏或错误
- 团队实践中总结出新的最佳实践

### 如何更新 steering 文件

1. 先更新架构定稿 v2 或实施计划 v2（如果需要）
2. 再更新对应的 steering 文件
3. 添加修订记录（日期 + 原因）
4. 提交 PR 并通过 Code Review
5. 合并后通知团队

### 修订记录格式

```markdown
## 修订记录

- 2026-05-14：初始版本，基于架构定稿 v2 与实施计划 v2
- 2026-05-XX：更新 XXX 章节，原因：XXX
```

## 常见问题

### Q1：steering 文件与架构定稿 v2 冲突怎么办？

**A**：以架构定稿 v2 为准。先更新架构定稿 v2，再更新 steering 文件。

### Q2：steering 文件太长，如何快速查找？

**A**：使用目录跳转或搜索功能。每个 steering 文件都有清晰的章节结构。

### Q3：是否可以创建项目特定的 steering 文件？

**A**：可以。但必须遵循 Skill Creator 规范，并确保与现有 steering 文件不冲突。

### Q4：steering 文件是否会自动更新？

**A**：不会。需要手动维护。建议在每个里程碑结束后检查是否需要更新。

### Q5：如何确保团队成员都遵循 steering 文件？

**A**：通过 Code Review 强制执行。所有 PR 必须通过 Code Review 规范检查。

## 联系方式

如有问题或建议，请：
1. 在项目仓库提 Issue
2. 在团队内部讨论
3. 提交 PR 改进 steering 文件
