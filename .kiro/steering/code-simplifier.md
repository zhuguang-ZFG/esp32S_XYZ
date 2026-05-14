---
name: code-simplifier
description: 代码简化与重构规范——最小改动、无 scope creep、测试先行
type: steering
priority: P1
inclusion: auto
---

# 代码简化规范 - Code Simplifier Standard

**版本**: v1.0
**创建日期**: 2026-05-15
**状态**: 生效中
**基准**: `docs/架构定稿-v2.md`

## §1 核心原则

```
最小改动 → 只改 spec 明确列出的文件和函数
测试先行 → 重构前必须有测试覆盖
禁止顺手 → 绝不"顺便"重构无关代码
```

## §2 简化模式

### 模式 1：删除死代码

**触发条件**：grep 全仓库未发现调用者。

**步骤**：
1. `rg "函数名"` 全仓库确认无引用
2. 删除函数/方法
3. 编译验证

**项目实际案例**（M1.7）：
```bash
# 删除 U8 G-code 回退路径
# 已删除：LoadJob / StartJob / CancelJob / PauseJob / ResumeJob
# 已删除：self.motor.jog / self.motor.set_laser_power / self.motor.job_*
# 文件：firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/dlc_motor_control_p1_ai_board.cc

# 验收命令：
rg "LoadJob|StartJob|\\$J=G91|M3 S|send_gcode" firmware/u8-xiaozhi
# 应仅在 Grbl 内部出现，不在 U8 板级文件中出现
```

### 模式 2：简化条件表达式

**触发条件**：三元同值、恒真/恒假分支、死分支。

**项目实际案例**（M1.3）：
```cpp
// 简化前：
"error_code": (sys.state == Alarm ? "null" : "null")

// 简化后：常量 + 注释
"error_code": "null"  // status 帧 error_code 恒为 null，错误码走独立 error 帧（v2 §15.4）

// 文件：firmware/u1-grbl/Grbl_Esp32/src/Report.cpp:857-859
```

### 模式 3：提取常量

**触发条件**：魔法数字出现 3+ 次。

**步骤**：
1. 定位所有出现位置
2. 提取为命名常量
3. 常量名反映语义（不反映值）

### 模式 4：合并重复代码

**触发条件**：同一逻辑在 2+ 处出现，差异仅参数。

**步骤**：
1. 确认语义完全相同（不是碰巧相似）
2. 提取为单一函数
3. 差异部分参数化

### 模式 5：简化嵌套

**触发条件**：嵌套深度 > 3 层。

**步骤**：
1. 早返回减少 else 分支
2. 提取内层为独立函数
3. 守卫子句前置

## §3 禁止事项（绝对）

| 禁止行为 | 原因 | 检测方式 |
|----------|------|----------|
| 跨里程碑重构 | 引入非计划变更 | `git diff` 与 spec 文件清单对比 |
| 无测试覆盖的重构 | 破坏语义无感知 | 强制前置步骤 |
| "顺手"改无关文件 | scope creep | `git diff --stat` 检查文件数 |
| 改变函数语义 | "简化"不应改行为 | 测试回归 |
| 删除"看起来没用"的注释 | 注释可能负载隐式约束 | 确认来源后再删 |
| 修改不在 spec 中的文件 | 违反 SPC 纪律 | 参见 spc-planning.md S4 |

## §4 重构检查清单

### 4.1 重构前

```
□ spec 是否明确授权此重构？
□ 是否已阅读相关架构定稿-v2 章节？
□ 是否确认了重构范围（文件清单）？
□ 是否有现有测试可作回归？
```

### 4.2 重构中

```
□ 改动是否在 spec 文件清单内？
□ 每步改动后编译是否通过？
□ 是否引入了新的魔法数字？
□ 是否改变了任何对外接口（函数签名/返回值/错误码）？
```

### 4.3 重构后

```
□ 编译通过？
□ 现有测试全绿？
□ git diff 文件数 = spec 文件清单数？
□ 文档是否需要同步更新？
```

## §5 验收命令模板

```bash
# 死代码删除验证
rg "被删除的函数名" 目标目录   # 应为 0 结果

# 编译验证（U1）
& 'C:\Users\zhugu\.platformio\penv\Scripts\platformio.exe' run -e release_esp32s3

# 编译验证（U8）
idf.py build  # ESP-IDF 环境

# Python 测试
python -m unittest discover -s tests -p "test_*.py" -v

# Java 测试
# ./mvnw test  [WAIT_ENV: mvn 环境就绪后]
```

## §6 修订记录

- 2026-05-15：初始版本，基于 M1.7（G-code 回退删除）、M1.3（alarm_code 简化）实际案例
