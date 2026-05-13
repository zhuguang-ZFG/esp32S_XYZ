# U1 Grbl 适配说明

本文件说明 `U1 / MOTOR_MCU` 在 `Grbl_Esp32` 中的当前适配方式。

## 1. 机型文件

当前默认机型已经切换为：

- [firmware/u1-grbl/Grbl_Esp32/src/Machines/dlc_motor_control_p1.h](/C:/Users/zhugu/Desktop/xue/esp32S_XYZ/firmware/u1-grbl/Grbl_Esp32/src/Machines/dlc_motor_control_p1.h)

默认选择文件：

- [firmware/u1-grbl/Grbl_Esp32/src/Machine.h](/C:/Users/zhugu/Desktop/xue/esp32S_XYZ/firmware/u1-grbl/Grbl_Esp32/src/Machine.h)

## 2. 轴定义

这块板不是普通 `XYZA`，而是按 `XYYZ` 接线：

- `J5 / XMOTOR` -> `X`
- `J1 / Y1MOTOR` -> `Y`
- `J3 / Y2MOTOR` -> `Y2`
- `J4 / ZMOTOR` -> `Z`

Grbl 里当前按：

- `N_AXIS = 3`
- `Y2` 作为 `Y` 的 ganged motor

这样做的目的，是让 `Y` 和 `Y2` 支持双电机龙门回零校正。

## 3. 步进输出映射

| 轴 | STEP | DIR |
|---|---|---|
| X | `GPIO46` | `GPIO3` |
| Y | `GPIO8` | `GPIO18` |
| Y2 | `GPIO17` | `GPIO16` |
| Z | `GPIO6` | `GPIO5` |

共享电机使能：

- `MOTOR_EN` -> `GPIO4`

## 4. 原点 / 限位输入映射

四路传感器当前映射为：

| 传感器输入 | Grbl 角色 | GPIO |
|---|---|---|
| `UUT_SENSOR_1` | `X_LIMIT_PIN` | `GPIO9` |
| `UUT_SENSOR_2` | `Y_LIMIT_PIN` | `GPIO12` |
| `UUT_SENSOR_3` | `Y2_LIMIT_PIN` | `GPIO13` |
| `UUT_SENSOR_4` | `Z_LIMIT_PIN` | `GPIO14` |

这对应你的要求：

- `X` 轴有原点检测
- `Y` 轴有原点检测
- `Z` 轴有原点检测

并且由于板上额外提供了一路传感器，当前还支持：

- `Y2` 独立原点检测

## 5. 回零策略

当前默认回零顺序：

1. `Z`
2. `X`
3. `Y + Y2`

并启用了：

- `DEFAULT_HOMING_ENABLE = 1`
- `DEFAULT_HARD_LIMIT_ENABLE = 1`
- `DEFAULT_SOFT_LIMIT_ENABLE = 1`
- `DEFAULT_HOMING_SQUARED_AXES = bit(Y_AXIS)`
- `LIMITS_TWO_SWITCHES_ON_AXES = 1`

这意味着：

- 上电后需要先回零
- `Y` 和 `Y2` 在回零时会按双开关做龙门校正

## 6. 当前假设

当前适配依赖以下假设：

1. 板上 `UUT_SENSOR_1..4` 均作为原点输入使用
2. 传感器极性以当前默认值 `DEFAULT_INVERT_LIMIT_PINS = 0` 为起点
3. 机械结构为 `X + 双Y + Z`

如果上电后发现限位状态常亮或回零方向不对，需要优先检查：

1. `Grbl` 参数 `$5` 限位反相
2. `Grbl` 参数 `$23` 回零方向反相
3. 机械接线和传感器装配极性

## 7. 风险提示

当前映射是按原理图网名直接落的。

其中最需要重点验证的是：

- `GPIO46` 被用于 `X_STEP`

这在实际上板前必须优先确认是否满足 `ESP32-S3` 的引脚能力约束。如果该脚在实测中不能稳定输出步进脉冲，需要第一时间回到硬件或引脚分配方案调整。
