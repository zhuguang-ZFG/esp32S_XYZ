---
name: spc-planning
description: Kiro SPC 模式执行规划，用于把 esp32S_XYZ 的任务固定为 Spec -> Plan -> Code -> Verify 的可靠编码流程
type: steering
priority: P0
inclusion: auto
---

# Kiro SPC 模式执行规划 v1.1

## 0. 文档定位

本文件是 Kiro 在本仓库执行编码任务时的 P0 级工作规程。它只规定“如何可靠完成任务”，不替代产品架构、实施计划和硬件报告。

权威顺序固定如下：

1. `docs/架构定稿-v2.md`：系统语义、分层边界、协议原则、安全原则的最高依据。
2. `docs/实施计划-v2.md`：M0~M6 的阶段、顺序、前置条件和完成判定。
3. `docs/接续指令.md`：当前会话续作任务的最高优先级入口。
4. `docs/硬件核对报告.md` 与 `docs/硬件连接与GPIO分配说明.md`：硬件事实、GPIO、风险表依据。
5. `docs/编码任务索引-v2.md`：编码任务落到真实目录、文件和验收命令的索引。
6. `.kiro/steering/*.md`：执行纪律，只能约束 Kiro 行为，不能改写上面文档的结论。

若 steering 与 `docs/` 冲突，必须以 `docs/` 为准，先更新或报告冲突，不得继续写代码。

## 1. 当前项目状态

当前日期基线：2026-05-15。

项目仍处于 `M0 设计期验证`。不要把仓库中已经静态落地的 M1/M2 片段误判为“当前阶段已进入 M2”。阶段推进必须看 `docs/实施计划-v2.md`、`docs/M0-进度报告.md` 和 `docs/接续指令.md`。

当前接续任务：

- `M0g 硬件核对报告` 进行中。
- 轮 1 已完成：ESP32-S3 strapping / GPIO 风险核对。
- 轮 2 已完成：HR4988E 步进驱动核对，固件已设置 `STEP_PULSE_DELAY=1 us`，新增 `R-007` / `R-008`。
- 下一步轮 3：核对 `ES8311` / `ES7210`，优先确认 I2C 地址、I2S 主从/时钟方向、MCLK/BCLK/LRCK/SDIN/SDOUT、电源去耦、上电复位和 `PA_EN` 时序。

已知不可绕过风险：

- `R-001`：U1.IO3 / HR4988E DIR 无外部下拉，strapping 风险保持 open。
- `R-007`：HR4988E `ROSC=1K` 偏离 datasheet 推荐用法。
- `R-008`：HR4988E CP1/CP2 `22nF` 低于 datasheet 推荐 `0.1uF/50V`。
- `M0f`：实物上电抽测等实物，不阻塞 M1~M3 的 fake 轨开发，但阻塞真实硬件联调和真实机械动作承诺。

## 2. SPC 流程

SPC = Spec -> Plan -> Code -> Verify。Kiro 必须按顺序执行：

1. **Spec**：确认任务来源、边界、输入输出、前置条件、不可做事项、验收命令。
2. **Plan**：把变更拆到真实文件和真实函数，说明顺序、风险、回退方式。
3. **Code**：只改 Plan 中列出的文件；若发现必须扩大范围，先回到 Spec/Plan。
4. **Verify**：运行与变更相关的本地验证；不能运行的要说明原因和替代证据。
5. **Sync**：代码、文档、计划状态必须同步；必要时提交并推送。

禁止跳步：

- 未读相关 `docs/` 就编码。
- 未确认真实文件路径就创建新文件。
- 未列出验收命令就声称任务完成。
- 验证失败后只改文档掩盖失败。
- 为了通过测试降低契约、安全或硬件约束。

## 3. 任务选择规则

Kiro 选择下一步任务时必须按以下顺序：

1. 先读 `docs/接续指令.md` 的“当前接续指令”。
2. 再读 `docs/实施计划-v2.md` 的对应小节。
3. 若是硬件任务，再读 `docs/硬件核对报告.md` 和相关 SCH/PCB/PADS/BOM/PDF/TXT。
4. 若是编码任务，再读 `docs/编码任务索引-v2.md` 的对应任务。
5. 若任务互相冲突，停止并报告冲突，不自行改阶段顺序。

当前不要主动开启 M3/M4/M5/M6；除非用户明确指定，优先收敛 M0g、M0c.2、M0c.3、M0d、M1 fake 轨准备。

## 4. 架构边界红线

所有代码必须遵守 `docs/架构定稿-v2.md` 的角色边界：

- `Client` 只表达用户意图和订阅状态，不直连 `DeviceServer`，不懂 U1 私有协议。
- `BusinessServer` 负责账号、鉴权、设备归属、任务持久化、资源授权和安全前置裁决，不直接和设备通道对话，不生成 Grbl 方言。
- `DeviceServer` 负责设备会话、语音链路、与 U8 的实时连接，不维护业务持久化，不越过 BusinessServer 下发业务任务。
- `U8` 负责把 DeviceServer 消息翻译为 U1 私有协议，不做运动闭环，不暴露 Grbl 方言给上层。
- `U1` 负责运动执行、状态机和最终安全裁决，不理解自然语言，不管理云端会话。

通信边界固定：

- Edge-A：`Client <-> BusinessServer`
- Edge-B：`BusinessServer <-> DeviceServer`
- Edge-C：`DeviceServer <-> U8`
- Edge-D：`U8 <-> U1`

禁止的捷径：

- Client 直连 DeviceServer。
- BusinessServer 直接编码 U1 私有协议或 G-code。
- DeviceServer 直接写 BusinessServer 数据库。
- U8 恢复 `$J=G91`、`M3 S`、`send_gcode` 等上层旁路。
- U1 接收 `write_text`、`draw` 等 L3 语义。

## 5. 证据规则

Kiro 不能凭记忆或猜测写硬件、协议、接口结论。

必须先找证据：

- 文件路径：用 `rg --files` 或目录枚举确认存在。
- 函数/类/宏：用 `rg` 或源码定位确认存在。
- 协议字段：先查 `docs/schemas/`、`docs/架构定稿-v2.md`、`docs/实施计划-v2.md`。
- 硬件参数：优先 PDF/TXT/SCH/PCB/BOM/坐标文件，其次 datasheet；交付图形不能替代 netlist 证据。
- 第三方库/API：优先本仓库锁定版本、官方文档或源码。

不确定标注：

- `[DX]`：datasheet 或原始资料不可得。
- `[INFERRED]`：从源码或行为反推，非官方文档明示。
- `[WAIT_HW]`：必须等实物或仪器确认。
- `[WAIT_ENV]`：缺少本地环境，验证未完成。

凡带不确定标注的结论，不得写成“已确认”。

## 6. 编码约束

每个编码任务必须满足：

- 只修改与任务直接相关的文件。
- 不重构无关代码，不顺手格式化整仓。
- 不删除用户未要求删除的文件。
- 不还原用户已有工作区改动。
- 不引入新框架、新服务、新协议，除非对应 spec 明确要求。
- 不把临时调试代码、硬编码 token、私钥、真实账号写入仓库。
- 不吞异常；错误必须映射到已定义错误码或显式补 spec/schema。
- 新增 capability、错误码、协议字段前必须先改 schema/文档，再改实现。
- `STOP` 与 `ESTOP` 语义绝对分离；不能用 reset 或硬中断伪装成受控 `STOP` 成功。
- fake 环境只模拟协议行为，不模拟真实电机物理，不用 fake 结论替代 M0f 实测。

## 7. Plan 文件要求

复杂任务必须创建或更新对应 plan。Plan 至少包含：

- 任务 ID 与来源文档章节。
- 目标和非目标。
- 前置条件检查结果。
- 将修改的文件清单。
- 将触碰的函数/类/宏清单。
- 具体实现步骤。
- 验证命令。
- 文档同步项。
- 风险与回退方式。

禁止出现无法执行的描述：

- “可能要改某些文件”
- “大概实现一下”
- “测试差不多通过”
- “顺便优化”

## 8. 验证门禁

提交前至少运行与变更相关的验证。常用命令：

```powershell
rtk python tools/check_gpio.py
rtk python tools/test_check_gpio.py -v
rtk python tools/validate_schemas.py
rtk python -m unittest discover -s tests -p "test_*.py" -v
rtk python tools/fake_u1/test_fake_u1.py -v
rtk python -m unittest tools.fake_u1.tests.test_app tests.ci.test_fake_integration -v
rtk powershell -NoProfile -Command "git diff --check"
```

U1 固件相关变更还必须尝试：

```powershell
rtk powershell -NoProfile -Command "& 'C:\Users\zhugu\.platformio\penv\Scripts\platformio.exe' run -e release_esp32s3"
```

验证原则：

- 文档-only 变更至少跑 `git diff --check`，并检查引用路径存在。
- schema 变更必须跑 `tools/validate_schemas.py`。
- GPIO/硬件配置变更必须跑 `tools/check_gpio.py` 和对应单测。
- fake U1 变更必须跑 fake U1 单测和集成测试。
- U1 固件变更必须跑 PlatformIO release 构建。
- Java 服务变更优先跑对应 `mvn test`；环境缺失时标注 `[WAIT_ENV]` 并提供已做的静态证据。
- U8 ESP-IDF 变更优先跑对应构建；环境缺失时标注 `[WAIT_ENV]`，不能声称编译通过。

## 9. 硬件核对专项规则

硬件核对必须按 L1/L2/L3 分层：

- L1：引脚、网络、方向、电源域、GPIO 分配。
- L2：外围配置、电阻电容、地址脚、模式脚、时序脚。
- L3：应力、启动顺序、温升、实际仪器验证、量产风险。

硬件结论必须写入：

- `docs/硬件核对报告.md`
- 必要时同步 `docs/硬件连接与GPIO分配说明.md`
- 影响固件时同步对应 `firmware/**/config` 或 machine header
- 影响计划时同步 `docs/接续指令.md`、`docs/M0-进度报告.md`、`docs/实施计划-v2.md`、`docs/全局规划-Planning-with-Files.md`

禁止：

- 用 PADS 网络名中的旧型号覆盖 SCH PDF/BOM 的实际型号。
- 用坐标文件或封装名推断电气连接。
- 用“常见模块一般这样”替代 datasheet 或源文件。
- 在未核对电源域和上电时序前修改固件初始化顺序。

## 10. Git 与交付

交付前必须确认：

- `git status --short --branch` 只包含本任务文件。
- 暂存只包含本任务文件。
- commit message 能说明真实变更。
- push 后 `HEAD`、`origin/main` 对齐。

不得提交：

- `.deps_pdf/`
- `.pytest_cache/`
- 本地虚拟环境
- 临时下载文件
- 用户明确要求保留但未要求提交的工作区改动

若用户要求“更新文档、上传、推送”，完成验证后必须 commit + push。

## 11. 当前可派发任务模板

### M0g-03: ES8311 / ES7210 硬件核对

目标：

- 完成 `docs/硬件核对报告.md` §1.3 / §1.4。
- 核对 ES8311 / ES7210 的 I2C 地址、I2S 时钟方向、MCLK/BCLK/LRCK/SDIN/SDOUT、复位/上电、PA_EN、去耦与电源域。

前置证据：

- `docs/DLC_Motor_Control_P1_V1.0_260513SCH.pdf`
- `docs/DLC_Motor_Control_P1_V1.0_260513PCB.pdf`
- `docs/DLC_Motor_Control_P1_V1.0_260513.txt`
- `docs/DLC_Motor_Control_P1_V1.0_260513BOM.xls`
- `docs/硬件连接与GPIO分配说明.md`
- ES8311 / ES7210 datasheet 或 ESP-IDF 组件源码

完成判定：

- 报告中每个结论有数据源。
- 新风险进入风险登记表。
- 如影响固件配置，同步修改并跑对应验证。
- 更新接续指令和 M0 进度。

## 12. 修订记录

- 2026-05-15：重写为 UTF-8 中文；对齐 `docs/` 当前 M0g 状态；移除旧 M1/M2 误导状态；新增证据、边界、验证、硬件、Git 约束。
