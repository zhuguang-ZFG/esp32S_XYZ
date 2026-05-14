---
name: tdd
description: 测试驱动开发 Skill——先写失败测试 → 最小实现 → 重构，所有新功能开发、Bug 修复必须遵循
type: steering
priority: P1
inclusion: auto
---

# 测试驱动开发 Skill - TDD Standard

**版本**: v1.0
**创建日期**: 2026-05-15
**状态**: 生效中
**基准**: `docs/架构定稿-v2.md`, `docs/schemas/`

## §1 核心循环

```
RED    → 先写一条失败测试（验证 spec 合约的边界条件）
GREEN  → 写刚好让测试通过的最小实现
REFACTOR → 无行为变更的前提下优化结构

循环粒度：每条约 5~15 分钟
```

## §2 测试位置

| 组件 | 测试目录 | 框架 |
|------|----------|------|
| Python 工具/脚本 | `tools/*/tests/` 或 `tests/` | unittest / pytest |
| U1 固件 | 编译期断言 + fake U1 回归 | PlatformIO |
| U8 固件 | 编译期断言 | ESP-IDF |
| BusinessServer (Java) | `manager-api/src/test/` | JUnit 5 / MockMvc |
| DeviceServer (Python) | 当前仓库未规划独立测试目录 | pytest |
| Schema 校验 | `tools/validate_schemas.py` | jsonschema |

## §3 最小测试覆盖清单

每条 spec 实现必须至少包含：

```
□ 1 条 happy path 测试（正常输入 → 预期输出）
□ 1 条 error path 测试（非法输入 → 预期错误码）
□ 1 条边界条件测试（边界值/空值/超限值）
```

## §4 测试命名

```
test_{功能}_{场景}_{预期}

示例：
test_get_status_idle_returns_state
test_move_without_homing_returns_e001
test_path_segment_out_of_range_returns_e_out_of_range
```

## §5 禁止事项

```
❌ 先写实现再补测试
❌ 测试依赖外部服务（网络、数据库）但无 mock/fake
❌ 测试有随机性（时间、随机值）
❌ 测试之间依赖执行顺序
❌ 跳过测试（skip/pass without assertion）视为测试未写
```

## §6 验收命令

```bash
# Python 测试
python -m pytest tests/ -v
python -m unittest tools.fake_u1.tests.test_app -v

# CI 集成测试
python -m unittest tools.fake_u1.tests.test_app tests.ci.test_fake_integration -v

# Schema 校验
python tools/validate_schemas.py
```

## §7 修订记录

- 2026-05-15：初始版本，基于 fake U1 测试实践（13/13）和 CI 骨架
