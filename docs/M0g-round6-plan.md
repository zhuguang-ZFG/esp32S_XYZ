# M0g 轮 6 实施计划：电源树 / 激光 MOS / 肖特基 / 电感核对

**创建日期**: 2026-05-15
**状态**: 已完成（done-with-risks）
**目标**: 完成 `docs/硬件核对报告.md` §1.9~§1.13 的 L1/L2/L3 核对，并更新风险表、接续指令

## 0. 任务概述

### 0.1 目标

本轮覆盖 4 组器件：

- `ME3116 / LM1117 / LM317`：电源树与线性/开关稳压，重点核对输入输出电压、二极管 OR、反馈分压、热耗散与负载余量。
- `IRFZ44N`：激光/12V 负载低边 MOS，重点核对 `LASER_CONTROL`、栅极串阻/下拉、源漏极、J12 接口与 IO45 strapping 风险。
- `SS36`：电源输入、续流/钳位与 USB/12V 路径肖特基，重点核对电流、电压和热余量。
- `FNR4030S220MT`：两路 buck 电感，重点核对电感值、饱和电流/温升余量和实际负载闭环证据。

### 0.2 非目标

- 不修改 PCB、固件 GPIO 或电源启动策略。
- 不承诺 12V、+3V3、AU_3V3、+5V_A 在实机负载下已经通过；实机上电归入 M0f。
- 不用器件命名经验替代 PADS、BOM、datasheet 或实测证据；缺失证据标注 `[DX]` 或登记风险。

### 0.3 前置条件检查

- [x] 轮 1~5 已完成并登记 `R-001~R-019`
- [x] PADS `.txt`、SCH PDF、BOM 已在 `docs/` 下
- [x] 硬件连接文档已记录 `12V`、`+3V3`、`AU_3V3`、`+5V_A`、`USB5V_VCC` 与 `J12`
- [x] PADS 初查确认 `U22/U14=ME3116`、`U13=LM1117`、`U32=LM317_ADJ`
- [x] PADS 初查确认 `Q5=IRFZ44NPBF`
- [x] PADS 初查确认 `S1/S2/S7/S8/S9=SS36`
- [x] PADS 初查确认 `L1/L2=FNR4030S220MT`

## 1. 将读取的文件

- `docs/DLC_Motor_Control_P1_V1.0_260513.txt`
- `docs/DLC_Motor_Control_P1_V1.0_260513SCH.pdf`
- `docs/DLC_Motor_Control_P1_V1.0_260513BOM.xls`
- `docs/硬件连接与GPIO分配说明.md`
- `docs/硬件核对报告.md`

## 2. 将修改的文件

- `docs/硬件核对报告.md`：新增 §1.9~§1.13，更新 §A 风险表、§A.2、§A.3、§B
- `docs/接续指令.md`：轮 6 完成后推进到后续 M0a/M0b
- `docs/M0g-round6-plan.md`：记录本轮进度

## 3. 实施步骤

### 步骤 1: 识别电源树与器件位号

- [x] `U22=ME3116`，12V 经 `S2` 到 VIN，输出 `+3V3`
- [x] `U14=ME3116`，12V 经 `S9` 到 VIN，输出 `AU_3V3`
- [x] `U13=LM1117`，USB5V 经 `S7` 到 VIN，输出接 `+3V3`
- [x] `U32=LM317_ADJ`，12V 输入，输出 `+5V_A`
- [x] `Q5=IRFZ44NPBF`，`R121=47` 串栅，`R120=100K` 下拉

### 步骤 2: 电源稳压 L1/L2/L3

- [x] 核对 ME3116 pin map：`BS/GND/FB/EN/IN/SW`
- [x] 核对 U22/U14 的输入二极管、反馈分压、电感和输出网络
- [x] 核对 U13 的 USB5V 到 +3V3 辅助供电路径
- [x] 核对 U32 的 LM317 分压值和 +5V_A 线性热耗散
- [x] 登记电源树负载/启动顺序/热余量风险

### 步骤 3: IRFZ44N 激光低边开关

- [x] 核对 `U1.IO45 -> LASER_CONTROL -> R121 -> Q5.G`
- [x] 核对 `Q5.S -> DGND`、`Q5.D -> J12`、`J12` 与文档引脚映射差异
- [x] 核对 IRFZ44N 在 3.3V 栅压下的导通余量
- [x] 关联 `R-003` 的 IO45 strapping 风险

### 步骤 4: SS36 / FNR4030 L2/L3

- [x] 核对 `S1/S8` buck 续流/钳位路径
- [x] 核对 `S2/S9` 12V 输入隔离路径
- [x] 核对 `S7` USB5V 到 U13 的输入路径
- [x] 核对 `L1/L2=FNR4030S220MT` 的电感值、饱和电流和热余量证据

### 步骤 5: 风险与接续

- [x] 新风险登记到 §A.1
- [x] 更新 §A.2 轮次状态
- [x] 更新 §A.3 交叉引用
- [x] 更新修订历史
- [x] 更新 `docs/接续指令.md`

## 4. 验证命令

```powershell
rtk powershell.exe -NoProfile -Command "git diff --check"
rtk powershell.exe -NoProfile -Command "git diff --check --no-index -- /dev/null docs/M0g-round6-plan.md"
rtk git status --short --branch
```

文档-only 变更不运行固件构建；若后续改到固件配置，则必须追加对应固件构建或明确 `[WAIT_ENV]`。

## 5. 当前发现

- 两路 ME3116 buck 拓扑重复：U22 输出 `+3V3`，U14 输出 `AU_3V3`。
- `U13=LM1117` 从 `USB5V_VCC` 经 `S7=SS36` 输入，输出也接 `+3V3`，与 U22 形成 +3V3 的双来源场景。
- `U32=LM317_ADJ` 从 `12V` 生成 `+5V_A`，分压电阻初步为 `470` 与 `1.5K`。
- `Q5=IRFZ44NPBF` 的 PADS 连接为 `Q5.S -> DGND`、`Q5.D -> J12.1`、`J12.2 -> 12V`；这与硬件连接文档 `J12-1=12V, J12-2=SWITCHED_GND` 存在引脚编号差异，需在报告中显式登记为待实物核对。

## 6. 完成判定

- [x] §1.9~§1.13 已写入 `docs/硬件核对报告.md`
- [x] ME3116 / LM1117 / LM317 / IRFZ44N / SS36 / FNR4030 均有 L1/L2/L3 结论
- [x] 新风险进入 §A.1 或明确无新增风险
- [x] `docs/接续指令.md` 已更新
- [x] 文档级验证通过
- [ ] commit/push 等待用户明确允许

## 7. 修订记录

- 2026-05-15: 初始版本，基于 `docs/接续指令.md` 轮 6 接续点创建
- 2026-05-15: 完成 §1.9~§1.13 报告写入；新增 `R-020~R-024`
- 2026-05-15: 更新 `docs/接续指令.md` 到 M0a JSON Schema 接续点
- 2026-05-15: `rtk git diff --check` 通过，仅有 LF/CRLF 提示；新文件 no-index 检查仅有 LF/CRLF 提示
