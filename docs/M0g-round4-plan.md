# M0g 轮 4 实施计划：摄像头 + 2V8 LDO 核对

**创建日期**: 2026-05-15
**状态**: 已完成（done-with-risks）
**目标**: 完成 `docs/硬件核对报告.md` §1.5 摄像头与 2V8 LDO 的 L1/L2/L3 核对

## 0. 任务概述

### 0.1 目标

识别本板摄像头接口使用的真实摄像头型号与 2V8 LDO 型号，并核对：

- DVP 数据/同步/时钟引脚与 U8 GPIO 是否一致
- SCCB/I2C 总线与摄像头控制脚是否一致
- `PWDN` / `RESET` 上电默认状态是否影响 strapping pin
- 2V8 电源来源、负载能力和去耦是否满足摄像头需求
- 现有 U8 固件 `config.h` / `InitializeCamera()` 是否与硬件一致

### 0.2 非目标

- 不修改 U8 摄像头驱动初始化代码
- 不承诺真实摄像头成像通过，实机验证归入 M0f
- 不用常见 OV/GC 模块经验替代 PADS、BOM、datasheet 或实机证据

### 0.3 前置条件检查

- [x] 轮 1 ESP32-S3 strapping / GPIO 核对已完成
- [x] 轮 2 HR4988E 核对已完成
- [x] 轮 3 ES8311 / ES7210 核对已完成，遗留风险已登记
- [x] PADS `.txt`、SCH PDF、BOM 已在 `docs/` 下
- [x] 摄像头型号已识别或确认不可得：仅确认 `J11=AXK724147G` 连接器，sensor/module 型号登记 `[DX]`
- [x] 2V8 LDO 型号已识别：`U56=ME6211C28M5G`
- [x] 摄像头 datasheet 已取得或登记 `[DX]`
- [x] 2V8 LDO datasheet 已取得或登记 `[DX]`

## 1. 将读取的文件

- `docs/DLC_Motor_Control_P1_V1.0_260513.txt`
- `docs/DLC_Motor_Control_P1_V1.0_260513SCH.pdf`
- `docs/DLC_Motor_Control_P1_V1.0_260513BOM.xls`
- `docs/硬件连接与GPIO分配说明.md`
- `docs/硬件核对报告.md`
- `firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/config.h`
- `firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/dlc_motor_control_p1_ai_board.cc`

## 2. 将修改的文件

- `docs/硬件核对报告.md`：新增 §1.5 摄像头 + 2V8 LDO 核对，更新 §A 风险表、§A.2、§B
- `docs/接续指令.md`：轮 4 完成后推进到轮 5
- `docs/M0g-round4-plan.md`：持续记录本轮进度

## 3. 实施步骤

### 步骤 1: 识别器件

- [x] 从 PADS 元件表和 BOM 查摄像头连接器 / 模块位号
- [x] 从 PADS 元件表和 BOM 查 2V8 LDO 位号、型号、封装
- [x] 若只有连接器没有摄像头型号，标注 `[DX]`，不得推断为 OV2640/GC 系列

### 步骤 2: L1 引脚层核对

- [x] DVP 数据线 `D0~D7`
- [x] 同步线 `VSYNC/HREF/PCLK`
- [x] `XCLK`
- [x] SCCB/I2C `SDA/SCL`
- [x] `PWDN/RESET`
- [x] 摄像头连接器电源脚：`2V8` / `+3V3` / `DGND`

### 步骤 3: L2 配置层核对

- [x] U8 固件 `config.h` pin 定义与 PADS 网络一致
- [x] `InitializeCamera()` 使用的 XCLK 频率、SCCB port、frame/pixel 配置是否有硬件前提
- [x] `PWDN` 默认电平是否能在 U8 复位阶段约束摄像头输出
- [x] 2V8 LDO 输入、输出、使能、反馈/固定输出配置

### 步骤 4: L3 应力层核对

- [x] 2V8 LDO 输出电流裕量（已登记 `R-016`，需真实 module 电流闭环）
- [x] 2V8 去耦与摄像头启动浪涌（已登记 `R-016`）
- [x] DVP 线负载与 strapping pin：重点回填 `R-002` / `R-005`
- [x] `IO40_DVP_PWDN` 是否需要默认上/下拉

### 步骤 5: 风险与接续

- [x] 新风险登记到 §A.1
- [x] 如能闭环 `R-002` / `R-005`，更新状态和依据
- [x] 更新 §A.2 轮次状态
- [x] 更新修订历史
- [x] 更新 `docs/接续指令.md`

## 4. 验证命令

```powershell
rtk powershell.exe -NoProfile -Command "git diff --check"
rtk git status --short --branch
```

文档-only 变更不运行固件构建；若后续改到固件配置，则必须追加 U8 构建或明确 `[WAIT_ENV]`。

## 5. 当前发现

- PADS 元件表确认 `J11 = AXK724147G`，24 pin board-to-FPC / board-to-board connector；这是摄像头接口，不是摄像头 sensor 本体，因此摄像头型号仍为 `[DX]`。
- Panasonic 官方页面确认 `AXK724147G` 为 24 pin、0.4mm pitch、1.5mm mated height、额定 `0.3A/pin`、总 pin 电流最大 5A 的 socket。
- PADS 元件表确认 `U56 = ME6211C28M5G`，固定 2.8V LDO，SOT-23-5；`U56.1/VIN` 与 `U56.3/CE` 接 `+3V3`，`U56.5/VOUT` 输出 `2V8`。
- LCSC / Microne datasheet 资料显示 `ME6211C28M5G-N` 为 fixed 2.8V、SOT-23-5、450mA 级 LDO；ME6211 系列工作输入电压范围 1.2V~6.0V，典型静态电流 30uA；当前板上 `+3V3 -> 2V8` 压差约 0.5V。
- `2V8` 仅在 PADS 本页明确接到 `J11.11`，并有 `C112=1uF` 输出去耦、`C114=1uF` 接近摄像头接口。
- `DVP_PWDN` 通过 `R119=10K` 上拉到 `+3V3`，并接 `J11.8`；U8 固件把 `CAMERA_PIN_PWDN` 配为 `GPIO_NUM_40`，复位阶段外部上拉倾向于让摄像头保持 power-down。
- `AI_RESET` 接到 `J11.6`，但 U8 固件 `CAMERA_PIN_RESET = GPIO_NUM_NC`，`InitializeCamera()` 不直接驱动该 reset 线，需登记待固件/硬件确认项。
- U8 固件 DVP pin 定义与 PADS `J11` 网络一致：`D0~D7`、`VSYNC/HREF/PCLK/XCLK` 均能逐项对上。

## 6. 完成判定

- [x] §1.5 已写入 `docs/硬件核对报告.md`
- [x] 摄像头型号与 2V8 LDO 型号已识别，或按 `[DX]` 登记不可得
- [x] DVP/I2C/PWDN/RESET/电源脚均有数据源标注
- [x] `R-002` / `R-005` 被关闭或保留并补充证据
- [x] 新风险进入 §A.1
- [x] `docs/接续指令.md` 已更新
- [x] 文档级验证通过
- [ ] commit/push 等待用户明确允许

## 7. 修订记录

- 2026-05-15: 初始版本，基于 `docs/接续指令.md` 轮 4 接续点创建
- 2026-05-15: 完成 §1.5 报告写入；新增 `R-014/R-015/R-016`，`R-002/R-005` 改为 `open-with-mitigation`
- 2026-05-15: 更新 `docs/接续指令.md` 到轮 5；`rtk git diff --check` 通过，仅有 LF/CRLF 提示
