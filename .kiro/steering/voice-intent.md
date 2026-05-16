---
name: voice-intent
description: 语音意图 Skill——VAD → 唤醒词 → 声纹 → 意图分流四道关，白名单触发，chat/device 严格分流
type: steering
priority: P1
inclusion: auto
---

# 语音意图 Skill - Voice Intent

**版本**: v1.0
**创建日期**: 2026-05-15
**状态**: 生效中
**基准**: `docs/架构定稿-v2.md §6bis, §6ter`

## §1 四道关（架构定稿-v2 §6ter.2）

```
麦克风 ──► [1.VAD]──► [2.唤醒词]──► [3.声纹]──► [4.意图分流]──► BusinessServer
            过杂音      过随机话语    过非家庭成员    过非命令意图
```

每道关串联，失败即丢弃或降级：

| 关 | 位置 | 作用 | 失败处理 |
|----|------|------|----------|
| 1. VAD | U8 本地 | 滤无声段、低信噪比 | 静默丢弃 |
| 2. 唤醒词 | U8 本地 | 滤未带"小智"前缀 | 静默丢弃 |
| 3. 声纹 | DeviceServer | 滤未注册成员 | 陌生人模式（§6ter.6） |
| 4. 意图分流 | DeviceServer | chat_intent vs device_intent | 闲聊在 DeviceServer 闭环 |

## §2 意图分流

### 2.1 判定规则

```
LLM 输出 → 白名单关键词匹配

命中 device 白名单 → device_intent → 调用 BusinessServer /internal/v1/intent_submit
未命中 → chat_intent → DeviceServer 内部 TTS 闭环
```

### 2.2 device_intent 白名单

```
write_text | draw | home | move_abs | pause | resume | stop | estop | get_device_info
```

### 2.3 语音动作安全限制（架构定稿-v2 §6bis.3bis）

```
✅ 允许："往左一点""往右一点""抬高一点""回原点" → move_abs（受限位移）
❌ 禁止：语音提交任意绝对坐标
❌ 禁止：绕过 homed/safe_margin 校验
单次位移 ≤ 预设步长上限
```

## §3 声纹模式（架构定稿-v2 §6ter.4）

| 档位 | 行为 |
|------|------|
| voiceprint_off | 不做声纹，任何人可控 |
| voiceprint_loose | 写类任务需声纹，闲聊放行（默认） |
| voiceprint_strict | 闲聊+写类都需声纹 |

## §4 TTS 播报矩阵（架构定稿-v2 §6bis.4bis）

| source | 播报时机 |
|--------|----------|
| voice | accepted / done / failed |
| client | accepted / done / failed |
| button | failed（最小播报） |

```
running / progress：默认不播报
播报内容：模板短句，不朗读内部错误码
```

## §5 验收命令

```bash
# 白名单完整性检查
rtk rg "intent_submit|device_intent|chat_intent" server/xiaozhi-esp32-server/main/xiaozhi-server

# 声纹相关
rtk rg "voiceprint|voiceprint_off|loose|strict" server/xiaozhi-esp32-server/main

# TTS hint
rtk rg "tts_hint|task_started|task_done|task_failed" server/xiaozhi-esp32-server/main
```

## §6 修订记录

- 2026-05-15：初始版本，基于架构定稿-v2 §6bis/§6ter
