# M0g 轮 5 实施计划：HX711 / CH340 / PC357N3 核对

**创建日期**: 2026-05-15
**状态**: 已完成（done-with-risks）
**目标**: 完成 `docs/硬件核对报告.md` §1.6~§1.8 的 L1/L2/L3 核对，并更新风险表、接续指令

## 0. 任务概述

### 0.1 目标

本轮覆盖 3 类器件：

- `HX711`：压力/称重桥式传感器 ADC，重点核对供电、桥激励、差分输入、`PD_SCK/DOUT` 与 U1 GPIO。
- `CH340C`：U1/U8 双 Type-C USB-UART 与自动下载链路，重点核对 USB D+/D-、VCC/V3、TX/RX、DTR/RTS 到 `BOOT/RESET`。
- `PC357N3`：4 路原点/到位传感器光耦输入，重点核对 12V 输入侧限流、输出侧上拉/滤波、到 U1 GPIO 的极性。

### 0.2 非目标

- 不修改固件 GPIO 配置或驱动逻辑。
- 不承诺 HX711 读数、USB 下载、原点传感器实机通过；实机验证归入 M0f。
- 不用常见模块经验替代 PADS、BOM、datasheet 或实测证据。

### 0.3 前置条件检查

- [x] 轮 1~4 已完成并登记 `R-001~R-016`
- [x] PADS `.txt`、SCH PDF、BOM 已在 `docs/` 下
- [x] 已确认 U1 GPIO 基准文档包含 `HX711_SCLK/MISO`、`M_U0TXD/RXD`、`UUT_SENSOR_1~4`
- [x] HX711 datasheet 已取得或登记 `[DX]`
- [x] CH340C datasheet 已取得或登记 `[DX]`
- [x] PC357N3 datasheet 已取得或登记 `[DX]`

## 1. 将读取的文件

- `docs/DLC_Motor_Control_P1_V1.0_260513.txt`
- `docs/DLC_Motor_Control_P1_V1.0_260513SCH.pdf`
- `docs/DLC_Motor_Control_P1_V1.0_260513BOM.xls`
- `docs/硬件连接与GPIO分配说明.md`
- `docs/硬件核对报告.md`
- `firmware/u1-grbl/Grbl_Esp32/src/Machines/dlc_motor_control_p1.h`
- `firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/config.h`

## 2. 将修改的文件

- `docs/硬件核对报告.md`：新增 §1.6~§1.8，更新 §A 风险表、§A.2、§A.3、§B
- `docs/接续指令.md`：轮 5 完成后推进到轮 6
- `docs/M0g-round5-plan.md`：记录本轮进度

## 3. 实施步骤

### 步骤 1: 识别器件与位号

- [x] PADS 初查：`U15=HX711`
- [x] PADS 初查：`U9/U10=CH340C`
- [x] PADS 初查：`U171/U5/U6/U7=PC357N3`
- [x] 用 PADS/SCH PDF 交叉确认型号与封装；BOM 采购件后续仍可补强

### 步骤 2: HX711 L1/L2/L3

- [x] 核对 `U15` pin map：`AVDD/VFB/VBG/BASE/RATE/XI/PD_SCK/DOUT`
- [x] 核对 `J10` 四线桥式传感器：`SENSOR_VDD/DGND/SENSOR_S-/SENSOR_S+`
- [x] 核对 `HX711_SCLK -> U1.IO7`、`HX711_MISO -> U1.IO15`
- [x] 核对 RATE、XI、模拟前端外围和传感器激励电压是否有 datasheet 证据；遗留 `R-017`

### 步骤 3: CH340C L1/L2/L3

- [x] 分别核对 U1 侧 `U9` 与 U8 侧 `U10`
- [x] 核对 Type-C `J2/J9` D+/D- 到 CH340C 的连接与串阻/ESD/CC 电阻
- [x] 核对 `M_U0TXD/M_U0RXD`、`AI_U0TXD/AI_U0RXD` 与 CH340C `TXD/RXD`
- [x] 核对 DTR/RTS 自动下载到 `BOOT/RESET` / `AI_BOOT/AI_RESET` 的时序链路；遗留 `R-018`
- [x] 核对 VCC/V3/USB5V/+3V3 供电方式是否符合 CH340C datasheet

### 步骤 4: PC357N3 L1/L2/L3

- [x] 核对 4 路连接器：`J25/J6/J7/J8`
- [x] 核对输入侧 `12V -> 传感器 -> MTO_SENSORx_OUT -> 光耦 LED` 与限流电阻
- [x] 核对输出侧 `UUT_SENSOR_1~4` 到 `U1.IO9/12/13/14`
- [x] 核对输出侧上拉、RC 滤波与固件限位输入极性
- [x] 核对 PC357N3 LED 正向电流、CTR、输出耐压/电流是否有 datasheet 余量；遗留 `R-019`

### 步骤 5: 风险与接续

- [x] 新风险登记到 §A.1
- [x] 更新 §A.2 轮次状态
- [x] 更新 §A.3 交叉引用
- [x] 更新修订历史
- [x] 更新 `docs/接续指令.md`

## 4. 验证命令

```powershell
rtk powershell.exe -NoProfile -Command "git diff --check"
rtk git status --short --branch
```

文档-only 变更不运行固件构建；若后续改到固件配置，则必须追加对应固件构建或明确 `[WAIT_ENV]`。

## 5. 当前发现

- PADS 已确认 `U15=HX711`，`PD_SCK -> HX711_SCLK -> U1.IO7`，`DOUT -> HX711_MISO -> U1.IO15`。
- `J10` 是压力/称重传感器接口：`SENSOR_VDD/DGND/SENSOR_S-/SENSOR_S+`。
- PADS 已确认 `U9/U10=CH340C`，分别对应 U1 与 U8 的 Type-C USB-UART。
- PADS 已确认 `U171/U5/U6/U7=PC357N3`，对应 `UUT_SENSOR_1~4` 光耦输入链路；外部接口为 `J25/J6/J7/J8` 的 12V 三线传感器。
- `firmware/u1-grbl/Grbl_Esp32/src/Machines/dlc_motor_control_p1.h` 明确说明 HX711 未接入 Grbl probe 逻辑，压力感知另行处理。

## 6. 完成判定

- [x] §1.6~§1.8 已写入 `docs/硬件核对报告.md`
- [x] HX711 / CH340C / PC357N3 均有 L1/L2/L3 结论
- [x] 新风险进入 §A.1 或明确无新增风险
- [x] `docs/接续指令.md` 已更新
- [x] 文档级验证通过
- [ ] commit/push 等待用户明确允许

## 7. 修订记录

- 2026-05-15: 初始版本，基于 `docs/接续指令.md` 轮 5 接续点创建
- 2026-05-15: 完成 §1.6~§1.8 报告写入；新增 `R-017/R-018/R-019`
- 2026-05-15: 更新 `docs/接续指令.md` 到轮 6；`rtk git diff --check` 通过，仅有 LF/CRLF 提示
