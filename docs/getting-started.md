# Getting Started

本项目包含两个 ESP32-S3 固件和一套云端服务。以下是开发环境搭建、编译、烧录和测试的完整指南。

## 前置条件

| 工具 | 版本 | 用途 |
|------|------|------|
| Python | 3.12+ | 测试、工具脚本 |
| PlatformIO CLI | 最新 | U1 固件编译 |
| ESP-IDF | v5.4+ | U8 固件编译 |
| Java JDK | 21 | manager-api 编译 |
| Node.js | 20+ | manager-mobile 编译 |
| Git | 2.30+ | 版本控制 |

## 快速开始

```bash
# 克隆仓库
git clone https://github.com/zhuguang-ZFG/esp32S_XYZ.git
cd esp32S_XYZ

# 运行所有测试（无需硬件）
make test

# 编译 U1 固件
make build-u1

# 编译 U8 固件（需要 ESP-IDF 环境）
make build-u8
```

## U1 固件（MOTOR_MCU）

### 技术栈

- **芯片**: ESP32-S3
- **框架**: Arduino (via PlatformIO)
- **基线**: Grbl_Esp32
- **机器配置**: `firmware/u1-grbl/Grbl_Esp32/src/Machines/dlc_motor_control_p1.h`

### 编译

```bash
cd firmware/u1-grbl
pio run                          # 编译默认环境 (release_esp32s3)
pio run -e release_esp32s3       # 显式指定环境
```

### 烧录

```bash
# J2 Type-C (CH340C) — 自动检测端口
pio run -t upload

# 指定端口
pio run -t upload --upload-port COM3

# 波特率: 921600 (platformio.ini 中 upload_speed)
```

### 监控串口

```bash
pio device monitor -b 115200
```

### 注意事项

- CH340C 自动下载电路 (DTR#/RTS# → BOOT/RESET) 的时序未经充分验证，如遇烧录失败，手动按 BOOT 键重试
- 机器配置通过 `Machines/dlc_motor_control_p1.h` 中的 `#define` 控制，修改需重新编译

## U8 固件（AI_MCU）

### 技术栈

- **芯片**: ESP32-S3
- **框架**: ESP-IDF v5.4+
- **基线**: xiaozhi-esp32 v2.2.6
- **板配置**: `firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/`

### 环境搭建

```bash
# 安装 ESP-IDF（如果尚未安装）
# 参考 https://docs.espressif.com/projects/esp-idf/en/latest/esp32s3/get-started/

# 激活 ESP-IDF 环境
. $HOME/esp/esp-idf/export.sh    # Linux/macOS
# 或 Windows: %USERPROFILE%\esp\esp-idf\export.bat
```

### 编译

```bash
cd firmware/u8-xiaozhi
idf.py build                      # 编译默认板型 (dlc-motor-control-p1-ai)
```

板型通过 `sdkconfig.defaults` 中的 `CONFIG_BOARD_TYPE_ZHUGUANG_DLC_MOTOR_CONTROL_P1_AI=y` 选择。

### 烧录

```bash
# J9 Type-C (CH340C)
idf.py -p COM4 flash              # Windows
idf.py -p /dev/ttyUSB0 flash      # Linux

# 烧录 + 监控
idf.py -p COM4 flash monitor
```

### 注意事项

- ESP-IDF 版本必须 ≥ 5.4（使用 ESP-SR 语音唤醒）
- 首次编译会下载组件，需要网络连接
- 板型选择在 `main/CMakeLists.txt` 中通过 Kconfig 条件编译（700+ 行 if/else 链）

## 云端服务

### DeviceServer (Python)

```bash
cd server/xiaozhi-esp32-server/main/xiaozhi-server
# Docker 方式
docker-compose up -d

# 或直接运行
pip install -r requirements.txt
python app.py
```

### BusinessServer (Java)

```bash
cd server/xiaozhi-esp32-server/main/manager-api
mvn clean package
java -jar target/manager-api.jar
```

### Mobile Client (uni-app)

```bash
cd server/xiaozhi-esp32-server/main/manager-mobile
pnpm install
pnpm dev                          # 开发模式
pnpm build                        # 构建微信小程序
```

## 测试

### 运行所有测试

```bash
make test
```

### 分类运行

```bash
# Schema 验证
python tools/validate_schemas.py

# GPIO 静态检查
python tools/check_gpio.py

# Python 单元测试
python -m pytest tests/ci/ -v

# Fake 集成测试（无需硬件）
python -m pytest tests/ci/test_fake_integration.py -v
```

### Fake 模拟器

无需硬件即可测试的模拟器：

| 模拟器 | 目录 | 用途 |
|--------|------|------|
| fake_u1 | `tools/fake_u1/` | 模拟 U1 运动控制 MCU |
| fake_ai | `tools/fake_ai/` | 模拟 U8 AI MCU |
| fake_device_server | `tools/fake_device_server/` | 模拟 DeviceServer |
| fake_lima_u8 | `tools/fake_lima_u8/` | 模拟完整 U8 设备 |

```bash
# 启动 fake_u1
cd tools/fake_u1 && python app.py

# 启动 fake_device_server
cd tools/fake_device_server && python app.py
```

## CI

GitHub Actions 在 push/PR 时自动运行：

1. **Schema 验证** — Edge-A/B/C/D JSON Schema 合规
2. **GPIO 检查** — strapping pin 风险检测
3. **Python 单元测试** — 243+ 测试用例
4. **Fake 集成测试** — 无硬件端到端验证
5. **Java 测试** — manager-api 76+ 测试
6. **Mobile 类型检查** — TypeScript 类型验证
7. **Markdown 链接检查** — 文档链接有效性

**注意**: U1/U8 固件目前不在 CI 中编译。固件 CI 需要额外配置 PlatformIO 和 ESP-IDF 环境。

## 硬件参考

- GPIO 分配: `docs/硬件连接与GPIO分配说明.md`
- 硬件核对报告: `docs/硬件核对报告.md`
- PCB 设计文件: `docs/` 目录下的 `.sch`、`.pcb`、BOM 文件
