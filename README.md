# esp32S_XYZ

双 ESP32-S3 板级整合项目 — AI 写字机（儿童/家庭创意教育产品）。

## 快速开始

```bash
make test          # 运行所有测试
make build-u1      # 编译 U1 固件
make build-u8      # 编译 U8 固件
make help          # 查看所有命令
```

详见 [docs/getting-started.md](docs/getting-started.md)。

## 目录结构

- `firmware/u1-grbl/` — U1 MOTOR_MCU 固件 (Grbl_Esp32, PlatformIO)
- `firmware/u8-xiaozhi/` — U8 AI_MCU 固件 (xiaozhi-esp32, ESP-IDF)
- `server/xiaozhi-esp32-server/` — 云端服务 (Python/Java/Vue/uni-app)
- `tools/` — 开发工具、Fake 模拟器
- `tests/` — CI 测试套件 (243+ 测试)
- `docs/` — 硬件文档、架构设计、里程碑报告
- `ops/` — 监控配置 (Prometheus/Grafana)

## 当前硬件分工

- **U1 / MOTOR_MCU** — 写字机/雕刻机运动控制，步进驱动、原点检测、舵机、压力传感器、激光
- **U8 / AI_MCU** — 小智语音交互，摄像头、I2S 音频、BLE/WiFi 配网、LCD 显示

## 通信架构

```
Client (微信小程序) ←Edge-A→ BusinessServer ←Edge-B→ DeviceServer ←Edge-C→ U8 ←Edge-D→ U1
```

4 条通信边界均有 JSON Schema 定义和验证。
