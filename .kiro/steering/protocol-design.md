---
name: protocol-design
description: 四条通信边界的协议设计 Skill——先 schema 后实现，每条字段有源，每个错误码有表
type: steering
priority: P1
inclusion: auto
---

# 协议设计 Skill - Protocol Design

**版本**: v1.0
**创建日期**: 2026-05-15
**状态**: 生效中
**基准**: `docs/架构定稿-v2.md §5, §15`, `docs/schemas/`

## §1 适用场景

- 新增/修改 Edge-A/B/C/D 任一协议字段
- 新增 capability 或事件类型
- 新增错误码
- Schema 校验规则变更

## §2 工作步骤（7 步）

```
1. 确定边界（Edge-A/B/C/D）
   → 参考架构定稿-v2 §5 四条通信边界定义

2. 查现有 schema
   → docs/schemas/{edge}/README.md
   → docs/schemas/{edge}/*.json

3. 新增/修改 schema 文件
   → 在对应 edge 目录下新增或修改 .json
   → 必须遵循 JSON Schema 2020-12

4. 写样例
   → docs/schemas/{edge}/examples/{name}.json
   → 至少 1 条 happy path + 1 条 error

5. 更新 schema README
   → docs/schemas/README.md 和边缘 README

6. 跑 schema 校验
   → rtk python tools/validate_schemas.py

7. 更新架构定稿-v2
   → 在对应小节追加字段说明
   → 附修订记录
```

## §3 四条边界速查

| 边界 | 传输 | 鉴权 | 消息风格 | schema 目录 |
|------|------|------|----------|-------------|
| Edge-A | HTTPS+WSS/TLS | JWT (client) | 客户端 capability | `edge_a/` |
| Edge-B | 内部 HTTP | Bearer (internal) | 设备 capability + event | `edge_b/` |
| Edge-C | WSS/TLS | 激活码+session token | 任务帧+事件帧 | `edge_c/` |
| Edge-D | UART `@{json}\n` | 物理连接 | U1 私有命令 | `edge_d/` |

## §4 字段定义规则

### 4.1 新增字段

```
□ 在 schema 中定义 type、description、examples
□ 若为可选字段，标记 "required": false 并有默认值
□ 若为必填字段，确认所有生产者和消费者都支持
□ 不得包含未定义的错误码引用
```

### 4.2 修改字段

```
□ 类型不变 → 更新 schema description/examples
□ 类型变更 → 新建字段，旧字段标记 deprecated
□ 枚举值变更 → 只能追加，不能删除
□ 必填变可选 → 允许，版本号不增加
□ 可选变必填 → 版本号 +1
```

## §5 错误码规则

所有错误码必须以 `E_` 前缀，在 `docs/架构定稿-v2.md §14` 中定义：

| 错误码 | 含义 | 使用边界 |
|--------|------|----------|
| E001 | 未归零，拒绝执行 | U1 → U8 (Edge-D) |
| E002 | 软限位 | U1 → U8 (Edge-D) |
| E005 | 硬限位触发 | U1 → U8 (Edge-D) |
| E006 | 归零失败 | U1 → U8 (Edge-D) |
| E007 | 任务被取消 | U1 → U8 (Edge-D) |
| E008 | 急停 | U1 → U8 (Edge-D) |
| E009 | 未分类告警 | U1 → U8 (Edge-D) |

**新增错误码必须先更新架构定稿-v2 §14，再更新实现。**

## §6 验收命令

```bash
# schema 校验
rtk python tools/validate_schemas.py

# 样例格式校验
for f in docs/schemas/*/examples/*.json; do
  rtk python -m json.tool "$f" > /dev/null || echo "FAIL: $f"
done

# 字段一致性检查
rtk rg "新增字段名" docs/schemas/ docs/架构定稿-v2.md
```

## §7 修订记录

- 2026-05-15：初始版本，基于 M0a 已完成的 schema 成果和架构定稿-v2 §5/§15
