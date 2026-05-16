---
name: milestone-acceptance
description: 里程碑验收 Skill——每个 M 结束后的验收流程、检查清单和门禁条件
type: steering
priority: P1
inclusion: auto
---

# 里程碑验收 Skill - Milestone Acceptance

**版本**: v1.0
**创建日期**: 2026-05-15
**状态**: 生效中
**基准**: `docs/实施计划-v2.md`, `docs/架构定稿-v2.md`, `spc-planning.md §6`

## §1 验收流程（4 步）

```
1. 检查完成判定
   → 对照实施计划-v2 对应 M 的完成判定逐项验证

2. 跑回归测试
   → 所有已完成的 spec 的验收命令全量回归
   → CI 全部 job 绿色

3. 更新文档
   → docs/实施计划-v2.md 标记状态
   → docs/M0-进度报告.md 更新进度
   → docs/全局规划-Planning-with-Files.md 更新指标

4. 同步 GitHub
   → commit + push
   → 更新接续指令
```

## §2 M0 验收检查表

```
□ M0a 契约固化
  - [ ] docs/schemas/ 全部 4 个 edge 目录有 README
  - [ ] 每条 capability/event/命令/响应有 schema

□ M0b 静态检查
  - [ ] python tools/check_gpio.py → 0 错误 0 警告
  - [ ] python tools/test_check_gpio.py → 8/8 通过

□ M0c 仿真器
  - [ ] M0c.1 fake U1：13/13 测试通过
  - [ ] M0c.2 fake DeviceServer：可用
  - [ ] M0c.3 fake AI：可用

□ M0d CI
  - [ ] 5 个 job 全部可用
  - [ ] GitHub main 分支全绿

□ M0e 基准修复
  - [ ] U8 config.h TX/RX 正确
  - [ ] 硬件文档证据强度标注

□ M0f 实物上电 → 等实物，不阻塞 M1 进入
```

## §3 M1 验收检查表

```
□ M1.1 Edge-D 样例集完备（7 样例）
□ M1.2 homed 字段真实（sys.is_homed）
□ M1.3 alarm_code/error_code 区分正确
□ M1.4 STOP/ESTOP 语义文档固化
□ M1.5 PAUSE/RESUME/STOP/ESTOP 命令可用
□ M1.6 home_position/position_mm 输出可用
□ M1.7 U8 G-code 回退路径已删除
□ M1.8 U8 编码层 3 条 capability 闭环
□ M1.9 fake U1 回归全绿
□ M1.10 U8 ↔ U1 最小链路全通（3 capability × 5 次）
```

## §4 M2 验收检查表

```
□ 三页面（登录/设备列表/设备详情）扫码可用
□ 点"归零"能在小程序看到状态变化
□ since_seq 补齐逻辑可用
□ 幂等提交去重可用
□ motion_event 五态落库正确
□ run_path 10 段方框端到端
□ tts_hint 任务开始/结束播报可触发
```

## §5 M3 验收检查表

```
□ 投影管线四阶全部有单测
□ write_text "你好" 端到端可执行
□ draw_generated 合格 SVG 可执行
□ 不合格图拒绝 + AI 重生成可用
□ 内容审核双层拦截可用（E_CONTENT_BLOCKED）
□ 安全裁决前置校验通过（E_NOT_HOMED/E_OUT_OF_RANGE/E_DEVICE_BUSY）
```

## §6 M4 验收检查表

```
□ VAD + 唤醒词可用
□ 声纹注册流程可用（录 5~8s → 嵌入向量）
□ 声纹实时比对可用（注册者通过 / 陌生人拒绝）
□ 三档模式可切换
□ 语音触发任务 demo 带声纹可用
□ 儿童 6 月重录提示可用
```

## §7 验收报告模板

```markdown
## 里程碑验收报告：M{编号}

### 完成判定
- [ ] 实施计划-v2 的完成判定项全部达到
- [ ] 回归测试全部通过
- [ ] CI 全绿

### 文档状态
- [ ] docs/实施计划-v2.md 已更新
- [ ] docs/M0-进度报告.md 已更新
- [ ] docs/架构定稿-v2.md 无漂移

### 风险更新
- 新增风险：{N} 条
- 已关闭风险：{N} 条

### 审批
- 验收人：{名字}
- 日期：{YYYY-MM-DD}
- 结论：✅ 通过 / ❌ 不通过
```

## §8 验收命令

```bash
# 全量回归
rtk python tools/check_gpio.py
rtk python tools/test_check_gpio.py -v
rtk python tools/validate_schemas.py
rtk python -m unittest tools.fake_u1.tests.test_app tests.ci.test_fake_integration -v

# CI 状态检查（需 gh CLI）
gh run list --workflow=ci.yml --branch main --limit 1
```

## §9 修订记录

- 2026-05-15：初始版本，基于 M0 已完成项和实施计划-v2 M0~M6 完成判定
