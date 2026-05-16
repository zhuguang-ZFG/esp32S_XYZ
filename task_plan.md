# Task Plan: M5/M6 设计期 → 可运行集成推进

**创建日期**: 2026-05-16
**状态**: in_progress
**依据**: 实施计划-v2.md, 接续指令.md, 架构定稿-v2.md

## 当前状态评估

- M0~M4 设计期静态契约：已完成
- M5/M6 设计期证据契约：已落地（commit 023648d）
- CI 测试：243 passed
- Java 测试：76 passed (AppV2, Safety, EdgeA)
- Schema 校验：62/62 passed
- GPIO 检查：OK
- 小程序 type-check：OK
- 工作树：干净（仅 .deps_pdf/ 未跟踪）

## 下一步优先级分析

按 Superpowers 原则和实施计划 v2 的里程碑顺序：

### 优先级 P0：确保远端 CI 可跑绿

当前所有验证都是本地的。需要确认：
1. CI workflow 是否覆盖了 M5/M6 新增的测试文件
2. `python-unit` job 的 `discover -s tests` 是否能发现所有新增测试

### 优先级 P1：M5/M6 新增模块的集成完整性

M5/M6 新增了大量 Python 模块（OTA handler, voiceprint cache, self-check handler 等），
需要确认这些模块的 import 路径和依赖是否正确。

### 优先级 P2：推进实施计划中标记为"待硬件环境补验"的软件部分

M1.10 U8 私有协议直发 — 需要实机
M2.12 run_path 端到端 — U8 构建已通过，实机待验
M5.1~M5.8 — 需要实机

### 优先级 P3：文档同步

确保 docs/接续指令.md 反映最新状态。

## 执行计划

### Phase 1: CI 完整性验证 [complete]
- [x] 检查 python-unit job 是否能发现 M5/M6 新增测试 — discover 自动覆盖
- [x] 检查 manager-api-tests 是否覆盖 M5/M6 新增 Java 测试类 — 发现遗漏 AESUtilsTest，已补入
- [x] 如有遗漏，补齐 CI 配置 — AESUtilsTest 已加入 ci.yml

### Phase 2: M5/M6 fake DeviceServer 路由扩展 [complete]
- [x] 新增 self_check 路由
- [x] 新增 voiceprint_cache GET/POST 路由
- [x] 新增 firmware_plan 路由
- [x] 新增 firmware_install_result 路由
- [x] 新增 HTTP 级别测试（5 个）
- [x] 新增 fake 集成测试（3 个 M5 端到端）
- [x] 全量回归通过：246 CI tests + 31 fake tool tests

### Phase 3: CI 远端修复 [complete]
- [x] 修复 python-unit job 缺少 httpx 依赖
- [x] 修复 test_manager_mobile_privacy_permissions 读取 gitignored 文件
- [x] 修复 markdown-link-check 误报（bilibili/feishu/paypal/sourceforge + Windows 路径）
- [x] 三个修复 commit 已推送，CI 正在运行

## 决策记录

- 2026-05-16: 确认所有设计期工作已提交，工作树干净
- 2026-05-16: 本地 CI 等价验证全部通过
- 2026-05-16: 远端 CI 6/7 jobs 通过（python-unit 缺 httpx 已修复）
- 2026-05-16: markdown-link-check 外部链接误报已通过 ignorePatterns 修复
