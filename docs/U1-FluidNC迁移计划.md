# U1 固件迁移到 FluidNC — 迁移计划与验证清单

> **风险等级**：高（涉及固件重写和硬件验证）
> **预计工作量**：5-7 天（含硬件调试）
> **对标参考**：[FluidNC](https://github.com/bdring/FluidNC)
> **创建时间**：2026-07-02

## 一、迁移背景

### 1.1 为什么迁移

| 维度 | Grbl_Esp32（现状） | FluidNC（目标） |
|------|---------------------|-----------------|
| 维护状态 | 已停更（最后提交 2023） | 活跃维护 |
| 配置方式 | C 头文件编译时硬编码 | YAML 运行时配置 |
| 安全更新 | 无 | 持续更新 |
| 社区支持 | 萎缩 | 活跃 |
| WiFi/WebUI | 内置但老旧 | 独立模块，可裁剪 |

### 1.2 迁移范围

- **U1 (MOTOR_MCU)**：ESP32-S3 + 4×HR4988E 步进驱动
- **不影响 U8 (xiaozhi-esp32)**：语音/MCU 独立，不涉及

## 二、配置翻译映射

已完成从 `dlc_motor_control_p1.h` 到 `dlc_motor_control_p1.yaml` 的翻译。

### 2.1 GPIO 映射对照

| 功能 | Grbl_Esp32 定义 | FluidNC YAML | 备注 |
|------|-----------------|-------------|------|
| X_STEP | GPIO46 | gpio.46 | strapping pin，已知风险 |
| X_DIR | GPIO3 | gpio.3 | strapping pin，JTAG 复用 |
| Y_STEP | GPIO8 | gpio.8 | |
| Y_DIR | GPIO18 | gpio.18 | |
| Y2_STEP | GPIO17 | gpio.17 | ganged motor |
| Y2_DIR | GPIO16 | gpio.16 | ganged motor |
| Z_STEP | GPIO6 | gpio.6 | |
| Z_DIR | GPIO5 | gpio.5 | |
| MOTOR_EN | GPIO4 | gpio.4 | 共享使能 |
| X_LIMIT | GPIO9 | gpio.9 | |
| Y_LIMIT | GPIO12 | gpio.12 | |
| Y2_LIMIT | GPIO13 | gpio.13 | ganged 限位 |
| Z_LIMIT | GPIO14 | gpio.14 | |
| LASER | GPIO45 | gpio.45 | PWM MOSFET |

### 2.2 运动参数对照

| 参数 | Grbl_Esp32 值 | FluidNC 字段 |
|------|---------------|-------------|
| X steps/mm | 80.0 | axes.x.steps_per_mm |
| Y steps/mm | 80.0 | axes.y.steps_per_mm |
| Z steps/mm | 400.0 | axes.z.steps_per_mm |
| X max rate | 6000 mm/min | axes.x.max_rate_mm_per_min |
| Y max rate | 6000 mm/min | axes.y.max_rate_mm_per_min |
| Z max rate | 1500 mm/min | axes.z.max_rate_mm_per_min |
| X accel | 200 mm/s² | axes.x.acceleration_mm_per_sec2 |
| Y accel | 200 mm/s² | axes.y.acceleration_mm_per_sec2 |
| Z accel | 80 mm/s² | axes.z.acceleration_mm_per_sec2 |
| Pulse | 4 µs | stepping.pulse_us |
| Dir delay | 1 µs | stepping.dir_delay_us |
| Idle lock | 25 ms | stepping.idle_ms |

### 2.3 回零策略对照

| 参数 | Grbl_Esp32 | FluidNC |
|------|-----------|---------|
| 回零顺序 | Z → X → Y | cycle 0/1/2 |
| 龙门校正 | bit(Y_AXIS) | square: true |
| Seek rate | 800 mm/min | seek_mm_per_min |
| Feed rate | 100 mm/min | feed_mm_per_min |
| Pulloff | 2.0 mm | pull_off_mm |
| Debounce | 250 ms | debounce_ms |
| 方向 | 负向 | positive_direction: false |
| 初始锁定 | HOMING_INIT_LOCK | init_lock: true |

## 三、迁移步骤

### 3.1 软件准备（Agent 已完成）

- [x] 翻译配置文件：`firmware/fluidnc/config/dlc_motor_control_p1.yaml`
- [x] 编写迁移文档（本文档）
- [ ] FluidNC 固件编译验证（需 PlatformIO 环境）

### 3.2 硬件验证（需人工操作）

- [ ] **D1: 备份当前 Grbl_Esp32 参数**
  ```bash
  # 通过串口连接 U1，执行：
  $$  # 打印所有参数，保存到文件
  $I  # 打印版本信息
  ```
- [ ] **D2: 烧录 FluidNC 固件**
  ```bash
  # 克隆 FluidNC
  git clone https://github.com/bdring/FluidNC.git
  cd FluidNC
  # 编译
  pio run -e fluidnc
  # 烧录
  pio run -e fluidnc -t upload
  ```
- [ ] **D3: 上传 YAML 配置**
  ```bash
  # 通过 FluidNC WebUI 或串口上传配置
  # 串口方式：
  $config=dlc_motor_control_p1.yaml
  ```
- [ ] **D4: 限位传感器验证**
  - 手动触发每个传感器，确认 FluidNC 状态报告中限位状态正确
  - 检查极性：若不对，修改 `limits_low_pin` → `limits_high_pin`
- [ ] **D5: 单轴运动验证**
  - 逐步测试 X、Y、Z 各轴的点动 (jog)
  - 确认方向正确（不对则修改 `direction_pin` 加 `!` 反相）
  - 确认 steps/mm 正确（用千分尺/标尺校验）
- [ ] **D6: 回零验证**
  - 执行 `$H`，观察回零顺序 Z → X → Y
  - 确认 Y/Y2 龙门校正工作正常
  - 确认回零后位置归零
- [ ] **D7: 激光/主轴验证**
  - 执行 `M3 S500` 开启激光
  - 确认 GPIO45 输出 PWM
  - 执行 `M5` 关闭
- [ ] **D8: 实际绘图/写字测试**
  - 运行一个简单 G-code 文件
  - 确认运动平稳、无丢步
  - 确认软限位生效

### 3.3 回退方案

如果 FluidNC 迁移失败：

1. 保留 Grbl_Esp32 固件和 `dlc_motor_control_p1.h` 不删除
2. 通过 ESP32 Flash 重新烧录 Grbl_Esp32 固件
3. 恢复 D1 步骤备份的 `$$` 参数

## 四、已知风险

### 4.1 strapping pins（高风险）

| GPIO | 风险 | 缓解措施 |
|------|------|---------|
| GPIO46 | 上电电平影响启动 | 实测确认 PCB 无外部下拉（已记录） |
| GPIO3 | JTAG 复用，上电时序 | 依赖内部弱上拉（已记录） |
| GPIO45 | VDD_SPI 电压选择 | 上电电平影响内部 flash 电压 |

FluidNC 对 strapping pin 的处理与 Grbl_Esp32 一致，但仍需实测验证。

### 4.2 通信协议兼容性

FluidNC 使用与 Grbl 兼容的串口协议（`$` 命令 + G-code），但：
- 部分 `$` 参数编号不同
- WebUI/API 接口不同
- LiMa 云端 → U1 的通信层需要适配验证

### 4.3 龙门校正差异

Grbl_Esp32 使用 `LIMITS_TWO_SWITCHES_ON_AXES` + `DEFAULT_HOMING_SQUARED_AXES`。
FluidNC 使用 `square: true` + ganged motor + 独立限位引脚。
逻辑等价但实现细节有差异，需重点验证 D6。

## 五、配置文件位置

- FluidNC YAML 配置：`firmware/fluidnc/config/dlc_motor_control_p1.yaml`
- 原 Grbl_Esp32 定义：`firmware/u1-grbl/Grbl_Esp32/src/Machines/dlc_motor_control_p1.h`
- 原适配说明：`docs/U1-Grbl适配说明.md`
