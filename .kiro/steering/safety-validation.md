---
name: safety-validation
description: 安全裁决 Skill——双重安全校验（BusinessServer 前置裁决 + U1 最终裁决），永不依赖云端下安全结论
type: steering
priority: P0
inclusion: auto
---

# 安全裁决 Skill - Safety Validation

**版本**: v1.0
**创建日期**: 2026-05-15
**状态**: 生效中
**基准**: `docs/架构定稿-v2.md §10bis, §14, §14bis`

## §1 核心原则

```
双重安全裁决 = BusinessServer 前置裁决 + U1 最终裁决

前置裁决：在任务下发到设备之前，校验越界、未归零、设备忙
最终裁决：U1 在运动执行中独立守卫限位、急停、力矩异常

两重裁决互相独立，任一失败即拒绝执行。
永不依赖云端网络来下安全结论。
```

## §2 前置裁决（BusinessServer）

### 2.1 实现位置（M3 阶段创建，当前不存在）

```
server/xiaozhi-esp32-server/main/manager-api/src/main/java/.../service/safety/
```

> 此目录当前不存在（M3 待实现）。SPC 规则：实现时先创建此目录，再写入代码。

### 2.2 校验项

| 校验项 | 拒绝条件 | 错误码 |
|--------|----------|--------|
| E_NOT_HOMED | 设备未归零且任务需要 homing | E_NOT_HOMED |
| E_OUT_OF_RANGE | 路径超出 workspace_mm × safe_margin_mm | E_OUT_OF_RANGE |
| E_DEVICE_BUSY | 设备正在执行其他任务 | E_DEVICE_BUSY |
| E_CONTENT_BLOCKED | 内容审核不通过 | E_CONTENT_BLOCKED |

### 2.3 工作区边界校验

```
workspace_mm：设备硬件可到达的范围
safe_margin_mm：软件安全边距（默认 5mm）

writable_area = workspace_mm - 2 × safe_margin_mm
任何路径点的 (x, y) 必须落在 writable_area 内。
```

## §3 最终裁决（U1）

### 3.1 实现位置

```
firmware/u1-grbl/Grbl_Esp32/src/
  ├── Limits.cpp         # 限位检测
  ├── MotionControl.cpp  # 运动控制与力矩/丢步检测
  └── System.cpp         # 告警状态机
```

### 3.2 触发条件

| 条件 | 动作 | 错误码 |
|------|------|--------|
| 硬限位开关闭合 | 立即停止所有轴运动 | E005 |
| 软件限位越界 | 减速停止 | E002 |
| 急停按钮按下 | 立即断电/断使能 | E008 |
| 力矩异常/堵转 | 立即停止 | E009 |

### 3.3 独立性要求

```
U1 安全裁决必须：
✅ 独立于 U8 云端连接状态
✅ 独立于 BusinessServer 前置裁决
✅ 在 U1 本地闭环（限位开关→GPIO→中断→停止）
❌ 不依赖 UART 通信正常
❌ 不等待云端确认
```

## §4 STOP vs ESTOP 硬约束

这是整个项目最重要的安全语义之一。违反即架构红线。

| 命令 | 性质 | 目标状态 | 业务结果 | 不得映射为 |
|------|------|----------|----------|------------|
| STOP | 受控停止 | IDLE | cancelled + E007 | mc_reset / hard interrupt |
| ESTOP | 紧急中断 | ESTOP | failed + E_ESTOP | 伪装成 STOP 成功 |

## §5 测试要求（spec 级别，实现时创建）

### 5.1 前置裁决单测（M3.5 实现时创建）

测试文件（待建）：`tests/test_safety_validator.py`

必须覆盖的测试函数：
```
test_not_homed_reject       # E_NOT_HOMED
test_out_of_range_reject    # E_OUT_OF_RANGE
test_device_busy_reject     # E_DEVICE_BUSY
test_content_blocked_reject # E_CONTENT_BLOCKED
```

### 5.2 U1 安全集成测试（待建）

测试文件（待建）：
```
tools/fake_u1/tests/test_limit_trigger.py    # 限位触发 → E005
tools/fake_u1/tests/test_estop_trigger.py    # 急停触发 → E008/ESTOP
tools/fake_u1/tests/test_stop_vs_estop.py    # STOP ≠ ESTOP 语义验证
```

> 以上测试文件当前均不存在。在对应 M3.5/M1 阶段实现时按本 spec 创建。

## §6 验收命令

```bash
# 前置裁决测试（M3.5 实现后激活）
# python -m pytest tests/test_safety_validator.py -v  [WAIT_ENV: 测试文件待 M3 创建]

# U1 安全裁决验证
rg "E005|E008|EmergencyStop|mc_reset" firmware/u1-grbl/Grbl_Esp32/src/

# STOP/ESTOP 语义检查
rg "STOP.*ESTOP|ESTOP.*STOP|STOP.*mc_reset" firmware/
```

## §7 修订记录

- 2026-05-15：初始版本，基于架构定稿-v2 §10bis 和 M1.4/M1.5 已完成的 STOP/ESTOP 语义固化
