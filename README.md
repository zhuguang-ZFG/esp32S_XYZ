# esp32S_XYZ

双 ESP32-S3 板级整合项目。

## 目录结构

- `docs/`
  - 硬件原理图与 PCB PDF
  - 板级连接与 GPIO 分配说明
- `firmware/u8-xiaozhi/`
  - `xiaozhi-esp32` 代码快照，计划用于 `U8(AI_MCU)`
- `firmware/u1-grbl/`
  - `Grbl_Esp32` 代码快照，计划用于 `U1(MOTOR_MCU)`

## 当前硬件分工

- `U1 / MOTOR_MCU`
  - 写字机/雕刻机/运动控制
  - 步进驱动、原点检测、舵机、压力传感器、激光
- `U8 / AI_MCU`
  - 小智、语音、视觉、交互
  - 摄像头、I2S 音频、I2C、SD 卡

## 说明

当前仓库先用于沉淀板级资料和两套固件基线代码，后续再做：

- GPIO 映射适配
- 双 MCU 串口协议
- 板级编译配置
