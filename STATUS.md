# esp32S_XYZ Status

> Updated: 2026-06-28
> Branch: main
> Tests: **115 passed, 0 failed**

## U1 固件瘦身：删除 26 个冗余机器配置（2026-06-28）

> U1（Grbl_Esp32）的 `Machines/` 目录原 28 个机器配置（3axis_v4/mpcnc/lowrider/tapster 等上游模板），实际只用 `dlc_motor_control_p1.h`（XYYZ 写字机）。
>
> | 指标 | 瘦身前 | 瘦身后 | 变化 |
> |------|--------|--------|------|
> | Machines 行数 | 3,182 | 205 | **-93.6%（删 2,977 行）** |
> | Machines 文件数 | 28 | 2 | 删 26 个 |
>
> **保留**：`dlc_motor_control_p1.h`（你的配置，Machine.h include 它）+ `test_drive.h`（Machine.h 注释推荐的初始测试配置）。
> **验证**：CI 静态测试 115 passed（无回归）；`dlc_motor_control_p1.h` 不依赖任何已删配置（引用检查通过）。

### U1 OTA 升级通道调研（WiFi/蓝牙关闭决策依据）

U1 固件升级有两条通道：
1. **platformio upload（串口烧录，upload_speed=921600）** — 开发/维护期烧录，通过 USB/串口，**不依赖 WiFi**
2. **WebUI `/updatefw`（WiFi OTA）** — 运行期远程升级，依赖 `ENABLE_WIFI`（Config.h:105）

**结论**：U1 是 MOTOR_MCU（运动控制），通过 UART 被 U8 控制，本身不需要网络。WiFi/蓝牙（6,410 行 WebUI）仅用于现场调试/OTA 便利。**关闭 WiFi/蓝牙是产品决策**：
- 若写字机不需要现场 WiFi 调试/远程升级 U1 → 可关（注释 Config.h:100,105，省 6K+ 行）
- 若需要现场调试 → 保留
- **本决策需用户确认**，未擅自关闭

### U1 加厚方向（待真机验证，非本轮代码改动）

1. **strapping pin 显式初始化**：IO46(X_STEP)/IO3(DIR)/IO45(LASER) 是 ESP32-S3 strapping pin，配置文件注释标注"实测无冲突"，建议上电时显式拉高消除启动时序依赖。
2. **激光安全联锁核对**：LASER_MODE 启用，需确认限位/急停时激光是否立即断电。
3. **运动参数实机标定**：STEPS_PER_MM/MAX_RATE/ACCELERATION 是 bring-up 保守值，需实机标定固化。

---

## U8 固件核心质量加固（2026-06-28）

> 瘦身后对 U8 核心代码做深度质量检查，修复 2 个 MEDIUM 健壮性问题：
>
> 1. **ReadU1Response 无上限保护（内存耗尽风险）** — `u1_protocol_client.cc:ReadU1Response` 的 `response.append` 无上限，U1 故障狂发数据时字符串无限增长耗尽堆。修复：加 `kU1MaxResponseBytes = 8KB` 上限（u1_protocol_client.h:26），超限 break + warning 日志。
> 2. **任务看门狗配置但未注册** — `sdkconfig:24 CONFIG_ESP_TASK_WDT_TIMEOUT_S=10` 配了看门狗，但无任务 `esp_task_wdt_add`，卡死不触发重启。修复：主循环（application.cc:189）`esp_task_wdt_add(NULL)` + 每轮 `esp_task_wdt_reset()`（line 193）；将 `portMAX_DELAY` 改为 5s 有限超时（kMainLoopWaitTicks，< 看门狗 10s），确保无事件时也能喂狗。
>
> **验证**：CI 静态测试 **115 passed, 169 subtests**（无回归）。两处改动均经代码一致性核对。
> **需用户验证**：本地 `idf.py build` 编译验证（watchdog 行为需真机确认无误触发）。

---

## U8 固件 board 瘦身（2026-06-28）

> **重大瘦身**：U8 删除 100 个冗余 board（xiaozhi-esp32 上游适配），只保留唯一编译目标 `zhuguang/dlc-motor-control-p1-ai`。
>
> | 指标 | 瘦身前 | 瘦身后 | 变化 |
> |------|--------|--------|------|
> | U8 main 总行数 | 120,438 | 35,623 | **-70.4%（删 84,815 行）** |
> | boards 行数 | 91,827 | 8,315 | -83,512（删 637 文件 / 99 目录）|
> | CMakeLists.txt | 1,228 | 509 | -719 |
> | Kconfig.projbuild | 982 | 399 | -583 |
>
> **改动**：
> - 删除 99 个冗余 board 目录（仅保留 `boards/common/` + `boards/zhuguang/`）。
> - 共享依赖迁移：`otto-robot/websocket_control_server.{cc,h}` → `boards/common/`（你的 board 真用到，迁入 common 保依赖）。
> - CMakeLists board 选择块：148 个 `if/elseif(CONFIG_BOARD_TYPE_*)` → 1 个（直接 set zhuguang，因唯一 board）。
> - Kconfig `choice BOARD_TYPE`：146 个 config → 1 个（ZHUGUANG）+ 清理 10 个死 choice 块（depends on 已删 board，永失效）。
>
> **验证**：
> - CI 静态测试：**115 passed, 169 subtests passed**（瘦身前后一致，无回归）。
> - 悬空引用检查：你的 board 的 11 个 include 全部解析到 `common/` 或 `zhuguang/`（含迁移后的 websocket_control_server）。
> - U8↔U1 核心代码（u1_protocol_client/motion_executor/motion_event_emitter）完整保留。
>
> **⚠️ 需用户验证（本仓库无 ESP-IDF 工具链）**：本地 `idf.py build` 编译验证 + 真机烧录。瘦身是纯删除操作，不改逻辑，风险集中在共享依赖完整性——已通过 include 追溯确认。

---

## 当前状态

> **2026-06-25 服务端瘦身**：服务端组件（xiaozhi-server / manager-api / manager-web / digital-human）已物理删除。
> 能力已由 LiMa 主项目（D:/QWEN3.0）集成。小程序已连接 LiMa 公网入口 <https://chat.donglicao.com>。
> 本仓库现仅保留 **固件** 与 **小程序** 两个客户端组件。

| 组件 | 状态 | 备注 |
|------|------|------|
| U1 固件 (Grbl_Esp32) | 已修复 P0/P1 | 缓冲区溢出、fallthrough、JSON 解析器、错误码 |
| U8 固件 (xiaozhi-esp32) | cJSON 一致性已修复 | BuildProtocolCommandJson 改用 cJSON |
| Mobile Client (uni-app) | TypeScript 类型检查通过 / 运行期日志清理 / 登录 i18n 补全 | 微信小程序，连 LiMa chat.donglicao.com |
| ~~xiaozhi-server (Python)~~ | **已删除 2026-06-25** | 能力由 LiMa 集成 |
| ~~manager-api (Java Spring Boot)~~ | **已删除 2026-06-25** | 能力由 LiMa 集成 |
| ~~manager-web (Vue.js)~~ | **已删除 2026-06-25** | 后台由 LiMa admin 接管 |
| ~~digital-human~~ | **已删除 2026-06-25** | /digital-human 由 LiMa 提供 |

## 测试

`
总测试:     111 passed, 0 failed (manager_mobile CI 已修复)
Schema:     62 验证通过
GPIO:       1 静态检查通过
固件单测:   40 passed (JSON 解析器 18 + U8 协议逻辑 22, g++ native)
`

已删除测试套件（manager_api 7 个 + xiaozhi_server 7 个 + monitoring 1 个 = 15 文件）。