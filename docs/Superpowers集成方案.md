# esp32S_XYZ Superpowers 集成方案

本文档按 Superpowers 风格整理：

- 先固定目标
- 再固定边界
- 再固定接入点
- 最后才进入实现

当前目标不是一次性把三套系统全改完，而是把 `U8`、`U1`、`server` 三部分的接入方式和代码落点定义清楚，避免后续修改失控。

## 1. 目标

将当前双 MCU 硬件板整合为一个可维护项目：

- `U8 / AI_MCU`
  - 基于 `xiaozhi-esp32`
  - 负责小智、语音、视觉、交互
- `U1 / MOTOR_MCU`
  - 基于 `Grbl_Esp32`
  - 负责写字机/雕刻机/运动控制
- `Server`
  - 基于 `xiaozhi-esp32-server`
  - 负责小智后端接入、WebSocket/MQTT/HTTP 能力

## 2. 验收标准

### 2.1 第一阶段验收

第一阶段以“结构正确”为主，不要求所有功能一次跑通。

验收标准：

1. `U8` 有独立板级目录和清晰 GPIO 映射
2. `U1` 有独立 machine header 和清晰 GPIO 映射
3. `U1 <-> U8` 串口协议有明确定义
4. `server` 的最简部署路径写入项目文档
5. 三部分代码在仓库中有稳定目录结构

### 2.2 第二阶段验收

第二阶段以“最小功能链路打通”为主。

验收标准：

1. `U8` 固件可编译
2. `U1` 固件可编译
3. `U8` 能连接 `xiaozhi-esp32-server`
4. `U8` 能通过串口向 `U1` 发送控制命令
5. `U1` 能响应基础运动命令和状态查询

## 3. 当前硬件分工

### 3.1 U8 角色

`U8` 对应 `AI_MCU`，建议保持为小智前端控制器。

已连接资源：

- 摄像头 DVP
- I2S 音频
- I2C
- SD 卡
- 功放使能
- 独立 USB 烧录口

### 3.2 U1 角色

`U1` 对应 `MOTOR_MCU`，建议保持为运动控制器。

已连接资源：

- 四路 TMC2208 步进驱动
- 四路原点传感器
- 三路舵机
- `HX711` 压力传感器
- 激光控制
- 独立 USB 烧录口

## 4. 仓库内代码落点

当前 `esp32S_XYZ` 仓库内目录如下：

- `docs/`
- `firmware/u8-xiaozhi/`
- `firmware/u1-grbl/`

后续建议按下面的方式继续演进：

- `docs/`
  - 硬件说明
  - 集成方案
  - 串口协议
  - server 部署说明
- `firmware/u8-xiaozhi/`
  - 在上游代码基础上新增本板 board
- `firmware/u1-grbl/`
  - 在上游代码基础上新增本板 machine header

## 5. U8 接入方案

### 5.1 结论

`xiaozhi-esp32` 已经提供了标准自定义板机制。当前最合适的做法不是覆盖现有板，而是新增一个本板 board 目录。

### 5.2 已确认的上游接入点

`xiaozhi-esp32` 中已有明确文档：

- `docs/custom-board.md`
- `docs/custom-board_zh.md`

文档要求新增板时至少处理：

1. 新建 board 目录
2. 编写 `config.h`
3. 编写 `config.json`
4. 编写板级 `*.cc`
5. 在 `main/Kconfig.projbuild` 注册板型
6. 在构建脚本或 `CMake` 分支里挂接该板目录

### 5.3 推荐板目录命名

建议新增目录：

- `firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/`

理由：

- 保留制造者命名空间
- 目录名稳定
- 后续如有 `P2/P3` 易于扩展

### 5.4 U8 最接近的参考板

从现有板型看，最值得参考的是带摄像头和音频的板：

- `main/boards/zhengchen-cam/`

它已经覆盖了我们最关心的几类资源：

- 摄像头 DVP
- I2S 音频
- I2C
- 按键

### 5.5 U8 GPIO 映射

按照当前硬件文档，`U8` 关键 GPIO 如下：

| 功能 | GPIO |
|---|---|
| `IO1_I2C_SDA` | `IO1` |
| `IO2_I2C_SCL` | `IO2` |
| `IO3_DVP_VSYNC` | `IO3` |
| `IO4_DVP_D6` | `IO4` |
| `IO5_DVP_XCLK` | `IO5` |
| `IO6_DVP_D5` | `IO6` |
| `IO7_DVP_PCLK` | `IO7` |
| `IO8_DVP_D2` | `IO8` |
| `IO9_DVP_D7` | `IO9` |
| `M_U1RXD` | `IO10` |
| `M_U1TXD` | `IO11` |
| `IO12_I2S_DI` | `IO12` |
| `IO13_I2S_WS` | `IO13` |
| `IO14_I2S_BCK` | `IO14` |
| `IO15_DVP_D4` | `IO15` |
| `IO16_DVP_D0` | `IO16` |
| `IO17_DVP_D3` | `IO17` |
| `IO18_DVP_D1` | `IO18` |
| `IO21_SD_DAT0` | `IO21` |
| `IO38_I2S_MCK` | `IO38` |
| `PA_EN` | `IO39` |
| `DVP_PWDN` | `IO40` |
| `IO45_I2S_DO` | `IO45` |
| `IO46_DVP_HREF` | `IO46` |
| `IO47_SD_CLK` | `IO47` |
| `IO48_SD_CMD` | `IO48` |
| `AI_BOOT` | `IO0` |
| `AI_RESET` | `EN` |

### 5.6 U8 代码改动边界

第一阶段只允许做这些改动：

1. 新增 board 目录
2. 新增 `config.h`
3. 新增 `config.json`
4. 新增板级 `*.cc`
5. 修改 `Kconfig.projbuild`
6. 修改板型选择的 `CMake`/构建分支

第一阶段不做：

- 大规模改动上游架构
- 改协议栈
- 改 UI 框架

### 5.7 U8 第一版功能目标

第一版建议只保最小闭环：

1. Wi-Fi 连网
2. 接入 `xiaozhi-esp32-server`
3. I2S 音频输入输出
4. 板载按键触发交互
5. 与 `U1` 串口通信

摄像头和 SD 卡可以先保留引脚映射，但允许延后验证。

## 6. U1 接入方案

### 6.1 结论

`Grbl_Esp32` 采用 machine header 选择机型。最稳妥的做法是新增一个专属机型头文件，而不是直接改现有 `TMC2209_4x.h`。

### 6.2 已确认的上游接入点

关键文件：

- `Grbl_Esp32/src/Machine.h`
- `Grbl_Esp32/src/Machines/*.h`

当前项目默认通过：

- `Machine.h`
- 或 `-DMACHINE_FILENAME=xxx.h`

选择目标机型。

### 6.3 推荐机型文件名

建议新增：

- `firmware/u1-grbl/Grbl_Esp32/src/Machines/dlc_motor_control_p1.h`

### 6.4 U1 GPIO 映射

| 功能 | GPIO |
|---|---|
| `MOTOR_EN` | `IO4` |
| `TMC2208-4_DIR` | `IO5` |
| `TMC2208-4_STEP` | `IO6` |
| `HX711_SCLK` | `IO7` |
| `HX711_MISO` | `IO15` |
| `TMC2208-3_DIR` | `IO16` |
| `TMC2208-3_STEP` | `IO17` |
| `TMC2208-2_DIR` | `IO18` |
| `TMC2208-2_STEP` | `IO8` |
| `TMC2208-1_DIR` | `IO3` |
| `TMC2208-1_STEP` | `IO46` |
| `UUT_SENSOR_1` | `IO9` |
| `M_U1TXD` | `IO10` |
| `M_U1RXD` | `IO11` |
| `UUT_SENSOR_2` | `IO12` |
| `UUT_SENSOR_3` | `IO13` |
| `UUT_SENSOR_4` | `IO14` |
| `SERVO_PWM1` | `IO21` |
| `SERVO_PWM2` | `IO47` |
| `SERVO_PWM3` | `IO48` |
| `LASER_CONTROL` | `IO45` |
| `BOOT` | `IO0` |

### 6.5 U1 与 Grbl 的映射建议

结合 PCB 丝印：

- `J5` -> `XMOTOR`
- `J1` -> `Y1MOTOR`
- `J3` -> `Y2MOTOR`
- `J4` -> `ZMOTOR`

因此推荐的轴定义不是简单 `XYZA`，而是更接近：

- `X = J5`
- `Y = J1`
- `A = J3` 或作为 `Y2`
- `Z = J4`

如果最终要支持双 Y 联动回零，建议机型按 `XYYZ` 理解，而不是普通 `XYZA`。

### 6.6 U1 第一版策略

第一版建议分两步：

#### 方案 A：先以 `XYZA` 跑通

优点：

- 接入 Grbl 最快
- 可以先验证 4 个驱动口都正常

缺点：

- 与实际机械语义不完全一致

#### 方案 B：再演进到 `XYYZ`

优点：

- 符合写字机/雕刻机实际结构

缺点：

- 需要更仔细处理双 Y 轴联动和限位策略

推荐执行顺序：

1. 先用 `XYZA` 跑通硬件
2. 再切到 `XYYZ` 机型语义

### 6.7 U1 第一阶段改动边界

第一阶段只做：

1. 新增 machine header
2. 固定 step/dir/enable/limit/laser 引脚
3. 明确串口保留
4. 预留舵机与压力传感器接口说明

第一阶段不做：

- 深改 Grbl 主流程
- 直接把小智协议嵌入内核

## 7. U1 <-> U8 串口协议

### 7.1 物理连接

- `U1.IO10` -> `U8.IO11`
- `U1.IO11` <- `U8.IO10`

### 7.2 第一版协议原则

协议先求稳，不追求复杂。

建议：

- 文本行协议
- `\n` 结尾
- UTF-8/ASCII 可读
- 一条命令一条响应

### 7.3 推荐第一版命令集

`U8 -> U1`

- `PING`
- `STATUS?`
- `HOME`
- `JOG X <delta>`
- `JOG Y <delta>`
- `JOG Z <delta>`
- `LASER 0`
- `LASER 1`
- `SERVO <id> <value>`

`U1 -> U8`

- `OK`
- `ERR <code> <message>`
- `STATUS IDLE`
- `STATUS RUN`
- `LIMIT <axis>`
- `POS X.. Y.. Z.. A..`

### 7.4 协议边界

第一版不要让 `U8` 直接透传任意 G-code。

原因：

- 边界太宽
- 风险太高
- 不利于前期稳定

先把高频动作收敛成有限命令，再决定是否开放 G-code 通道。

## 8. Server 接入方案

### 8.1 结论

`xiaozhi-esp32-server` 先按“最简 server-only”方式部署即可，不必一开始就上全模块。

### 8.2 已确认的上游文档

关键文档：

- `xiaozhi-esp32-server/docs/Deployment.md`
- `xiaozhi-esp32-server/docs/Deployment_all.md`

其中：

- `Deployment.md`：更适合第一阶段
- `Deployment_all.md`：功能全，但部署复杂度更高

### 8.3 第一阶段 server 目标

第一阶段只要求：

1. server 能跑起来
2. `U8` 能连上 WebSocket
3. 语音/对话链路可验证

### 8.4 推荐部署模式

优先推荐：

- Docker
- server-only

原因：

- 更容易复现
- 更适合先验证固件接入
- 少数据库和后台系统变量

### 8.5 部署要点

按上游文档，最小目录结构为：

```text
xiaozhi-server
  docker-compose.yml
  data/
    .config.yaml
  models/
    SenseVoiceSmall/
      model.pt
```

最小启动命令：

```bash
docker compose up -d
docker logs -f xiaozhi-esp32-server
```

`.config.yaml` 中重点关注：

- `server.websocket`
- LLM 配置
- ASR/TTS 配置

## 9. 推荐实施顺序

### 9.1 第一步

在 `firmware/u8-xiaozhi/` 中新增本板 board 目录，并完成：

- `config.h`
- `config.json`
- `*.cc`
- `Kconfig/CMake` 挂接

### 9.2 第二步

在 `firmware/u1-grbl/` 中新增：

- `src/Machines/dlc_motor_control_p1.h`

先按 `XYZA` 跑通驱动口和限位输入。

### 9.3 第三步

定义并实现最小串口协议：

- `PING`
- `STATUS?`
- `HOME`
- `JOG`
- `LASER`

### 9.4 第四步

部署 `xiaozhi-esp32-server` 最简模式，并让 `U8` 接入。

### 9.5 第五步

再做系统联调：

1. `U8` 收到语音/指令
2. `U8` 发串口命令给 `U1`
3. `U1` 执行动作并回状态

## 10. 当前不做的事

为了保持 Superpowers 的边界清晰，当前阶段明确不做：

- 不同时深改 `xiaozhi` 和 `Grbl` 主架构
- 不一开始就支持全部 MCP / MQTT / UDP / 控台模块
- 不先做花哨 UI
- 不先做复杂多轴补偿和高级运动学

## 11. 下一步具体落点

下一轮实现建议直接做这两件事：

1. 在 `firmware/u8-xiaozhi/` 新增 `dlc-motor-control-p1-ai` board
2. 在 `firmware/u1-grbl/Grbl_Esp32/src/Machines/` 新增 `dlc_motor_control_p1.h`

这样改动最小，路径也最干净。
