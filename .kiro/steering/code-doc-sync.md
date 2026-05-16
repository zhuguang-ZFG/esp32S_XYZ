---
name: code-doc-sync
description: 编码与文档同步强制规范——每个阶段性节点后必须更新对应文档，不允许代码先行文档后补
type: steering
priority: P0
inclusion: auto
---

# 编码文档同步规范 - Code-Doc Sync Standard

**版本**: v1.0
**创建日期**: 2026-05-15
**状态**: 生效中
**基准**: CLAUDE.md §5, `docs/架构定稿-v2.md`

## §1 触发节点

命中以下任一条件，必须立即同步文档（不可延迟到下一个节点）：

| 节点 | 示例 |
|------|------|
| 里程碑最后一项完成 | M0a 全部 schema 落完 |
| Capability 端到端跑通 | GET_STATUS 从 U8 → U1 → 回 U8 全通 |
| 芯片/接口/边界核对完成 | ES8311 I2S 引脚核对完成 |
| 基准修订 | 架构/协议/GPIO/错误码有变更 |
| Commit 对应闭合逻辑 | 非单文件保存的完整逻辑闭合 |

**不触发同步的例外**：纯 typo、注释修复、格式化、完全内部重构（行为不变、接口不变、文档无感知）。

## §2 更新文档清单

| 变更类型 | 必须更新的文档 |
|----------|---------------|
| 协议/错误码变更 | `docs/架构定稿-v2.md` + `docs/schemas/` + `docs/实施计划-v2.md` |
| GPIO/芯片/引脚变更 | `docs/硬件连接与GPIO分配说明.md` + `docs/硬件核对报告.md` |
| 里程碑进度 | `docs/M0-进度报告.md` + `docs/全局规划-Planning-with-Files.md` + `docs/实施计划-v2.md` |
| 能力/功能变更 | `README.md` + `docs/架构定稿-v2.md` |
| 固件配置变更 | 对应 `firmware/*/config` + `docs/硬件核对报告.md` |
| Schema 变更 | `docs/schemas/` + `docs/schemas/README.md` |

## §3 更新顺序

```
1. 先验证通过（schema 校验 / 单测 / 手动测试 / Superpowers 审视）
2. 立即更新对应文档
3. 文档与代码一并 commit
4. 文档没更新前，下一个节点不允许开始
```

**逆向禁止**：
```
❌ 代码先改完，说"文档后面补"
❌ 多个文档变更塞在一个 commit 而不与代码同 commit
❌ 文档写"TODO"占位就算完成
```

## §4 发现文档落后时的处理

```
1. 立即停下手头编码
2. 补齐文档
3. 检查是否影响架构定稿（若是，先对齐架构定稿）
4. 补齐后继续
```

## §5 验证命令

```bash
# 检查是否有未同步的代码变更
rtk git diff --stat

# 检查实施计划状态是否与文档一致
rtk rg "状态.*已完成|⏳|⏸️" docs/M0-进度报告.md docs/实施计划-v2.md

# 检查 schema 与代码字段一致性
rtk python tools/validate_schemas.py
```

## §6 修订记录

- 2026-05-15：初始版本，对齐 CLAUDE.md §5 与 Superpowers 原则
