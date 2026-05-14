---
name: fake-environment
description: Fake 环境 Skill——如何启动、配置、注入错误到 fake U1/DeviceServer/AI，主板到货前所有开发对 fake 做
type: steering
priority: P1
inclusion: auto
---

# Fake 环境 Skill - Fake Environment

**版本**: v1.0
**创建日期**: 2026-05-15
**状态**: 生效中
**基准**: `docs/实施计划-v2.md M0c`, `tools/fake_u1/README.md`

## §1 核心原则

```
fake 环境 = 协议行为模拟，不是物理模拟。
fake U1 只保留协议行为，禁止仿真真实电机物理。
fake 结论不能替代 M0f 实测。
```

## §2 Fake 环境清单

| Fake | 位置 | 协议 | 状态 |
|------|------|------|------|
| fake U1 | `tools/fake_u1/app.py` | Edge-D `@{json}\n` | ✅ 已完成 |
| fake DeviceServer | `tools/fake_device_server/` | Edge-B/C WSS/HTTP | ⏳ 待实现 |
| fake AI | `tools/fake_ai/` | HTTP (LLM/ASR/TTS) | ⏳ 待实现 |

## §3 Fake U1 使用

### 3.1 启动

```bash
# 默认端口 7799
python tools/fake_u1/app.py --port 7799

# 模拟 50ms 响应延迟
python tools/fake_u1/app.py --latency-ms 50
```

### 3.2 错误注入

```bash
# 注入未归零错误
python tools/fake_u1/app.py --inject E001

# 注入硬限位
python tools/fake_u1/app.py --inject E005

# 注入急停
python tools/fake_u1/app.py --inject E008
```

### 3.3 状态机

```
IDLE → (HOME) → HOMING → IDLE
IDLE → (MOVE) → RUNNING → IDLE
IDLE → (RUN_PATH) → RUNNING → IDLE
RUNNING → (PAUSE) → PAUSED → (RESUME) → RUNNING
* → (ESTOP) → ESTOP
* → (ERROR) → ERROR
```

### 3.4 测试

```bash
# 运行全部测试
python -m unittest tools.fake_u1.tests.test_app -v

# 13/13 通过为正常
```

## §4 Fake DeviceServer（待实现，规格先定）

### 4.1 输入

```
HTTP POST /internal/v1/motion_task  ← BusinessServer
```

### 4.2 输出

```
WSS JSON → fake U1（通过 TCP/串口转发）
WSS JSON ← fake U1 响应 → 转为 motion_event HTTP 回 BusinessServer
```

### 4.3 验收

```
BusinessServer 端单元测试能对它做集成 fake
不依赖真实 xiaozhi-server Python 进程
```

## §5 Fake AI（待实现，规格先定）

### 5.1 模式

```
LLM：固定回包（configurable prompt→response map）
ASR：固定文字（configurable audio→text map）
TTS：静默/短 beep 音频
```

### 5.2 验收

```
DeviceServer 在 ai_plan=plan_basic 时调 fake 而非真实 provider
```

## §6 验收命令

```bash
# fake U1
python tools/fake_u1/app.py --help
python -m unittest tools.fake_u1.tests.test_app -v
python -m unittest tests.ci.test_fake_integration -v

# fake U1 协议格式验证
echo '{"type":"command","command":"GET_STATUS"}' | nc localhost 7799
```

## §7 修订记录

- 2026-05-15：初始版本，基于 M0c.1 fake U1 已实现的 13/13 测试和 README
