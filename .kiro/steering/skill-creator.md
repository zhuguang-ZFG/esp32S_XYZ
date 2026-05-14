---
name: skill-creator
description: Kiro steering skill 创建与管理规范——确保每个 skill 有明确用途、场景和约束
type: steering
priority: P1
inclusion: auto
---

# Skill 创建与管理规范 - Skill Creator Standard

**版本**: v1.0
**创建日期**: 2026-05-15
**状态**: 生效中
**基准**: `.kiro/steering/README.md`

## §1 Skill 分类

| 类别 | 用途 | 示例 |
|------|------|------|
| 流程类 | 定义工作流程与纪律 | code-review, spc-planning, milestone-acceptance |
| 实现类 | 指导具体技术实现 | protocol-design, projection-pipeline, safety-validation |
| 工具类 | 工具使用方法 | fake-environment |
| 规范类 | 全局约束与标准 | chinese-communication, code-doc-sync |

创建优先级：**流程类 > 规范类 > 实现类 > 工具类**。

## §2 Skill 必须包含的 6 个要素

未包含全部 6 要素的 skill 视为"不合格 skill"：

1. **用途说明**：一句话说明解决什么问题
2. **适用场景**：何时触发此 skill（3+ 具体场景）
3. **核心约束**：3+ 条必须遵守的硬性规则
4. **工作步骤**：5~10 步可执行序列
5. **实例/模板**：实际项目中的例子或填空模板
6. **验收标准**：如何判断 skill 被正确执行

## §3 Skill 模板

```markdown
---
name: {skill-name}
description: {一句话描述}
type: steering
priority: {P0|P1|P2}
inclusion: auto
---

# {Skill 中文标题}

**版本**: v1.0
**创建日期**: {YYYY-MM-DD}
**状态**: 生效中
**基准**: {引用的架构/文档文件路径}

## §1 核心原则
<!-- 3~5 条全局适用的原则 -->

## §2 触发条件
<!-- 何时自动或手动加载此 skill -->

## §3 工作步骤
<!-- 每步可执行、可验证 -->

## §4 核心约束
<!-- 硬性规则，违反即中断 -->

## §5 禁止事项
<!-- 绝对不可做的事 -->

## §6 验收标准
<!-- 可执行的验收命令或检查项 -->

## §7 实例
<!-- 项目中已有的实际案例 -->

## §8 修订记录
```

## §4 创建流程（6 步）

```
1. 识别需求
   - 从实施计划-v2 对应里程碑中发现需要新 skill 的模式
   - 3+ 个子任务共享同一模式 → 值得创建 skill

2. 确定分类
   - 按 §1 分类
   - 确认不与现有 skill 重复

3. 编写初稿
   - 用 §3 模板
   - 全部 6 要素必填

4. 对标架构定稿
   - 检查与架构定稿-v2 是否有冲突
   - 有冲突以架构定稿-v2 为准

5. 注册到 README
   - 在 .kiro/steering/README.md 文件清单中添加
   - 在 README 目录树结构中添加

6. 提交评审
   - 创建 PR
   - 通过 code-review.md 审查
```

## §5 禁止创建的 Skill

| 类型 | 原因 |
|------|------|
| 过于宽泛（如 "coding.md"） | 无法提供具体指导 |
| 与架构定稿-v2 冲突 | 基准不能有二义性 |
| 纯理论、无实操步骤 | Kiro skill 必须可执行 |
| 重复现有 skill | 维护负担 |
| 跨项目通用（如 "git.md"） | 全局 skill 在 CLAUDE.md/RTK.md 中 |

## §6 Skill 生命周期

```
draft → active → deprecated → removed

- draft：刚创建，未评审
- active：评审通过，自动加载
- deprecated：被新 skill 取代，保留 30 天过渡
- removed：从仓库完全删除
```

## §7 质量检查清单

```
□ 是否包含全部 6 要素？
□ 是否至少有 3 条核心约束？
□ 是否包含实际项目案例？
□ 是否与架构定稿-v2 无冲突？
□ 是否已在 README 注册？
□ 是否有 frontmatter（name/description/type/priority/inclusion）？
```

## §8 修订记录

- 2026-05-15：初始版本，基于项目实施计划-v2 与已有 steering 实践
