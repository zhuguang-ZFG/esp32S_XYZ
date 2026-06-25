# esp32S_XYZ Status

> Updated: 2026-06-25
> Branch: main
> Tests: **93 passed, 18 failed**（pre-existing manager_mobile failures，与本次清理无关）

## 当前状态

> **2026-06-25 重大瘦身**：服务端组件（xiaozhi-server / manager-api / manager-web / digital-human）已物理删除。
> 能力已由 LiMa 主项目（D:/QWEN3.0）集成。小程序已连接 LiMa 公网入口 https://chat.donglicao.com。
> 本仓库现仅保留 **固件** 与 **小程序** 两个客户端组件。

| 组件 | 状态 | 备注 |
|------|------|------|
| U1 固件 (Grbl_Esp32) | 已修复 P0/P1 | 缓冲区溢出、fallthrough、JSON 解析器、错误码 |
| U8 固件 (xiaozhi-esp32) | cJSON 一致性已修复 | BuildProtocolCommandJson 改用 cJSON |
| Mobile Client (uni-app) | TypeScript 类型检查通过 | 微信小程序，连 LiMa chat.donglicao.com |
| ~~xiaozhi-server (Python)~~ | **已删除 2026-06-25** | 能力由 LiMa 集成 |
| ~~manager-api (Java Spring Boot)~~ | **已删除 2026-06-25** | 能力由 LiMa 集成 |
| ~~manager-web (Vue.js)~~ | **已删除 2026-06-25** | 后台由 LiMa admin 接管 |
| ~~digital-human~~ | **已删除 2026-06-25** | /digital-human 由 LiMa 提供 |

## 测试

`
总测试:     93 passed, 18 failed (manager_mobile 预存在失败)
Schema:     62 验证通过
GPIO:       1 静态检查通过
固件单测:   40 passed (JSON 解析器 18 + U8 协议逻辑 22, g++ native)
`

已删除测试套件（manager_api 7 个 + xiaozhi_server 7 个 + monitoring 1 个 = 15 文件）。