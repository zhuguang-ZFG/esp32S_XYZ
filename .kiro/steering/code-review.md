---
name: code-review
description: 全局代码审查规范——架构边界、安全裁决、协议合规、任务模型四维审查
type: steering
priority: P0
inclusion: auto
---

# 代码审查规范 - Code Review Standard

**版本**: v1.0
**创建日期**: 2026-05-15
**状态**: 生效中
**基准**: `docs/架构定稿-v2.md`、`docs/schemas/`

## §1 审查维度

每次审查从四维切入，任一维不通过则拒绝合并：

| 维度 | 检查重点 | 依据 |
|------|----------|------|
| 架构边界 | 角色职责、分层、通信边界 | 架构定稿-v2 §2, §3, §4, §5 |
| 安全裁决 | 双重安全校验、STOP/ESTOP 语义 | 架构定稿-v2 §10bis, §14 |
| 协议合规 | Schema 先于实现、字段演进规则 | 架构定稿-v2 §15, §19.5 |
| 任务模型 | 五态映射、幂等、seq 单调 | 架构定稿-v2 §6, §6bis |

## §2 架构边界审查

### 2.1 角色职责检查表（架构定稿-v2 §2）

```
□ Client 是否直连了 DeviceServer？        → 拒绝（§2bis）
□ Client 是否直接生成 segments？          → 拒绝（§3.2）
□ DeviceServer 是否操作了业务持久化？      → 拒绝（§3.3）
□ BusinessServer 是否生成了 G-code？       → 拒绝（§3.2）
□ U1 是否接收了 L3 语义？                 → 拒绝（§3.2）
□ U8 是否暴露了 Grbl 方言给上层？          → 拒绝（§2 角色表）
```

### 2.2 数据所有权检查表（架构定稿-v2 §3.3）

| 数据类型 | 所属角色 | 越权信号 |
|----------|----------|----------|
| 账号数据 | BusinessServer | 出现在 DeviceServer |
| 设备归属 | BusinessServer | 出现在 DeviceServer/U8 |
| 任务元数据 | BusinessServer | 出现在 DeviceServer |
| 任务实时状态 | DeviceServer（转发） | 落库（除 BusinessServer 持久化外） |
| 设备瞬时状态（Grbl 内部） | U1 | 泄露到上层 |
| 内容资源 | BusinessServer | 出现在 DeviceServer/U8 |

### 2.3 分层禁止项（架构定稿-v2 §3.2）

```
grep 检查清单：
  rg "xiaozhi-server" server/xiaozhi-esp32-server/main/manager-mobile  # 应 0 结果
  rg "G-code|G91|M3 S" server/xiaozhi-esp32-server/main/manager-api     # 应 0 结果
  rg "manager-api" firmware/u8-xiaozhi                                  # 应 0 结果
  rg "write_text|draw_generated" firmware/u1-grbl                       # 应 0 结果
```

## §3 安全裁决审查

### 3.1 双重裁决生效检查（架构定稿-v2 §10bis）

```
□ BusinessServer 前置裁决（homed / range / busy / safe_margin）
  文件位置：manager-api/.../service/safety/
  检查项：
    - E_NOT_HOMED 在未归零时拒绝写/画/移动任务
    - E_OUT_OF_RANGE 在越界时拒绝
    - E_DEVICE_BUSY 在设备执行中拒绝新任务

□ U1 最终裁决（限位 / 急停 / 力矩）
  文件位置：firmware/u1-grbl/Grbl_Esp32/src/
  检查项：
    - 限位触发 → E005（非 E006）
    - 急停触发 → E008
    - 安全裁决永不依赖云端
```

### 3.2 STOP vs ESTOP 语义（架构定稿-v2 §6.3, §14）

**绝对禁止混用**：

| 命令 | 目标状态 | 业务结果 | 禁止映射为 |
|------|----------|----------|------------|
| STOP | IDLE | cancelled (E007) | reset / hard interrupt |
| ESTOP | ESTOP | failed+E_ESTOP | 伪装成 STOP 成功 |

审查命令：
```bash
rtk rg "STOP.*ESTOP|ESTOP.*STOP" firmware/u1-grbl/Grbl_Esp32/src/Protocol.cpp
rtk rg "mc_reset.*STOP|STOP.*mc_reset" firmware/u1-grbl/Grbl_Esp32/src/Protocol.cpp
```

## §4 协议合规审查

### 4.1 Schema 先于实现（架构定稿-v2 §19.5）

```
□ 新增/变更字段前是否已更新对应 schema？
  检查路径：docs/schemas/{edge}/ 下对应文件

□ 实现中的字段名是否与 schema 一致？
  检查命令：python tools/validate_schemas.py（M0d 已配置）
```

### 4.2 字段演进规则

| 操作 | 允许 | 条件 |
|------|------|------|
| 新增可选字段 | ✅ | 必须先 schema |
| 新增必填字段 | ⚠️ | 版本号 +1 |
| 删除字段 | ❌ | 需新版本，至少一个版本过渡期 |
| 修改字段类型 | ❌ | 新字段 + 废弃旧字段 |
| 修改枚举值 | ❌ | 只能追加，不能删除 |

## §5 任务模型审查

### 5.1 五态映射（架构定稿-v2 §6.3）

```
□ accepted → U1 返回 ack.accepted=true
□ running  → U1 status: RUNNING/HOMING/PAUSED
□ done     → U1 result: DONE
□ failed   → U1 error 或超时
□ cancelled→ STOP 成功，对应 E007

检查：各态映射是否完整？是否存在未映射的 U1 状态？
```

### 5.2 幂等检查（架构定稿-v2 §6.1bis）

```
□ (account_id, device_id, request_id) 三元组去重
□ 去重窗口 24h
□ request_id 由客户端生成
□ 语音意图也遵守同一幂等规则
```

### 5.3 seq 单调（架构定稿-v2 §6.2）

```bash
# 断言：同一 device_id 的 seq 严格递增
rtk rg "seq" server/xiaozhi-esp32-server/main/manager-api
```

## §6 代码质量审查

### 6.1 无幻觉检查

```
□ 所有新增文件在 git diff 中可见
□ 所有引用函数/类可通过 grep 找到
□ 所有硬件参数有 datasheet/源码支撑（含 [DX]/[INFERRED] 标注）
□ 所有错误码在架构定稿-v2 §14 错误码表中存在
```

### 6.2 实现 vs 合约一致性

```
□ 实现产生的错误码是否都在 spec 错误码表中？
□ 实现修改的文件是否都在 spec 文件清单中？
□ 实现是否引入了 spec 边界之外的变更？
```

## §7 文档同步审查

参考 `code-doc-sync.md`：

```
□ 代码修改后是否同步更新了对应文档？
□ 架构定稿-v2 是否与实现一致？
□ docs/schemas/ 是否与实现字段对齐？
□ docs/硬件连接与GPIO分配说明.md 是否反映最新 GPIO 分配？
```

## §8 里程碑审查

### 8.1 M0 门禁

```bash
rtk python tools/check_gpio.py                           # 0 错误 0 警告
rtk python -m unittest tools.fake_u1.tests.test_app -v   # 全绿
rtk python tools/validate_schemas.py                     # 全绿
```

### 8.2 M1 门禁

```bash
# 三条 capability 各 5 次通过（fake U1 环境）
rtk rg "GET_STATUS|HOME|MOVE" firmware/u8-xiaozhi firmware/u1-grbl/Grbl_Esp32/src
```

### 8.3 M2+ 门禁（参见 spc-planning.md §6）

## §9 审查输出格式

审查完成后输出结构化报告：

```markdown
## Code Review: {PR标题}

### 架构边界
- [ ] 角色职责：{通过/拒绝，原因}
- [ ] 分层：{通过/拒绝，原因}
- [ ] 通信边界：{通过/拒绝，原因}

### 安全裁决
- [ ] 双重裁决：{通过/拒绝，原因}
- [ ] STOP/ESTOP 语义：{通过/拒绝，原因}

### 协议合规
- [ ] Schema 先于实现：{通过/拒绝，原因}
- [ ] 字段演进：{通过/拒绝，原因}

### 任务模型
- [ ] 五态映射：{通过/拒绝，原因}
- [ ] 幂等/seq：{通过/拒绝，原因}

### 结论
- {✅ 通过 / ❌ 拒绝}
- 拒绝原因总计：{N} 项
```

## §10 修订记录

- 2026-05-15：初始版本，基于架构定稿-v2 §2~§19 与 spc-planning.md §5
