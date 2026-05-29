# esp32S_XYZ Status

> Updated: 2026-05-29
> Branch: `main`
> Tests: **251 passed, 0 failed** (`pytest tests/ci/ -q`)
> Firmware CI: **10/10 全绿**（U1/U8 固件编译 + JSON 解析器单测）

## 当前状态

| 组件 | 状态 | 备注 |
|------|------|------|
| U1 固件 (Grbl_Esp32) | ✅ P0/P1 已修复 | 缓冲区溢出、fallthrough、JSON 解析器、错误码 |
| U8 固件 (xiaozhi-esp32) | ✅ cJSON 一致性已修复 | BuildProtocolCommandJson 改用 cJSON |
| **小智服务器** (xiaozhi-esp32-server) | ✅ | 整体状态正常 |
| └ DeviceServer (Python) | ✅ 运行中 | Docker 部署，WebSocket 设备会话管理 |
| └ BusinessServer (Java) | ✅ 76+ 测试通过 | Spring Boot，账号/设备/任务/审核 |
| └ Mobile Client (uni-app) | ✅ TypeScript 类型检查通过 | 微信小程序，设备控制 |
| └ Web Admin (Vue.js) | ✅ | 后台管理界面 |
| CI Pipeline | ✅ 10/10 全绿 | Schema/GPIO/Python/Java/Mobile/Link/U1编译/U8编译/单测 |
| 硬件验证 | ⏳ 待验证 | 需实物硬件环境 |

## 测试通过率

```
总测试:     251 passed, 0 failed, 199 subtests passed
Java 测试:  76+ passed (manager-api)
Schema:     62 验证通过
GPIO:       1 静态检查通过
固件单测:   18 passed (JSON 解析器, g++ native)
CI:         10/10 jobs 全绿
```

## 里程碑进度

| 里程碑 | 状态 | 说明 |
|--------|------|------|
| M0: Schema 设计 | ✅ 完成 | Edge-A/B/C/D JSON Schema |
| M1: 运动执行器 | ✅ 完成 | shell/git/network executor |
| M2: 代码上下文 | ✅ 完成 | tree-sitter + SQLite graph |
| M3: 管道集成 | ✅ 完成 | memory persistence + routing bridge |
| M4: 开发者技能 | ✅ 完成 | /investigate /review /ship /learn |
| M5: 设计验证 | ✅ 完成 | 5.1-5.8 全部通过 |
| M6: 合规验证 | ✅ 完成 | 6.1-6.8 全部通过 |
| 硬件在环测试 | ⏳ 待启动 | 需实物硬件环境 |

## 已知风险

| 风险 | 等级 | 说明 | 缓解措施 |
|------|------|------|----------|
| R-018 | 中 | CH340C 自动下载电路时序未验证 | 手动按 BOOT 键重试 |
| U8 单体文件 | 中 | board.cc 1089 行 | 待拆分（P2-7） |
| 无固件 CI | 中 | C/C++ 回归不可见 | 待配置 PlatformIO/ESP-IDF CI |
| ReturnValue variant | 低 | 59 处手动内存管理 | 待 RAII 重构（P2-15） |
| U8 CMake 700行 if/else | 低 | 板选择硬编码 | 待数据驱动重构（P2-11） |

## 最近修复（2026-05-29）

| Commit | 修复 |
|--------|------|
| 8f9271e | P0: 缓冲区溢出 + fallthrough + 认证溢出 |
| c54255f | P1: JSON 解析器加固 + 错误码分配 + API Key 脱敏 |
| 5fa5112 | 构建文档 + Makefile + README 更新 |
| 98d64f7 | U8 cJSON 一致性 |
| 7c7c1a8 | 审查报告修复记录 |
| 11eb54c | STATUS.md + getting-started.md rtk 前缀修复 |
| 463b1d4 | 固件 CI: U1 PlatformIO + U8 ESP-IDF v5.5.2 |
| 2a3e1ca | 固件单测: JSON 解析器 18 cases (g++ native) |

## 开发环境

| 工具 | 版本 | 状态 |
|------|------|------|
| Python | 3.12+ | ✅ |
| PlatformIO | 最新 | ✅ U1 可编译 |
| ESP-IDF | v5.4+ | ⚠️ 本机未安装 |
| Java JDK | 21 | ✅ |
| Node.js | 20+ | ✅ |
| ruff | 0.15.8 | ✅ |

## 快速命令

```bash
make test          # 运行所有测试
make build-u1      # 编译 U1 固件
make build-u8      # 编译 U8 固件
make lint          # 代码检查
make help          # 查看所有命令
```
