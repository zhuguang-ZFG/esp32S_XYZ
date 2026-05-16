# 代码质量审计报告 v2

> 日期：2026-05-17
> 审计范围：项目自定义代码（tools/、firmware 自定义板级、CI、schemas）
> 方法：静态分析 + 测试执行 + 安全扫描
> 基准：`docs/代码质量报告-v1.md`（2026-05-14）

---

## 0. 总体评估

| 维度 | 评分 | 说明 |
|------|------|------|
| 测试覆盖 | A | 251 个测试全部通过，覆盖 schema/GPIO/fake 仿真/集成 |
| Schema 契约 | A | 62 个 schema+example 校验全部通过 |
| GPIO 静态检查 | A | 无冲突、无 strapping pin 误用 |
| 安全性 | **F** | 发现 1 个硬编码 API 密钥泄露（红线） |
| CI 配置 | B+ | 完整但有路径引用问题 |
| 代码风格 | B+ | 整体良好，少量瑕疵 |

---

## 1. 红线问题（必须立即修复）

### 1.1 API 密钥硬编码泄露

**文件**: `tools/test_vectorize.py:7`

```python
API_KEY = 'sk-sp-djI.pO4-by3NZh1AD6lxgsUZqcFjfGrg2wwbZOwfD9vDR0m...'
```

- 阿里云 MaaS API 密钥明文写在源码中
- 该文件已被 git 追踪（仓库状态显示 untracked 的只有 `.deps_pdf/`）
- **影响**：任何能访问仓库的人可直接使用该密钥调用付费 API
- **修复**：立即轮换密钥 + 改为环境变量读取 + 加入 `.gitignore`

---

## 2. 黄线问题（建议尽快修复）

### 2.1 bare except 语句

**文件**: `tools/check_gpio.py:264`

```python
except:
    pass
```

应改为 `except (OSError, IndexError):` 或至少 `except Exception:`。
bare except 会吞掉 KeyboardInterrupt 和 SystemExit。

### 2.2 CI 中 Python 包导入路径问题

**文件**: `.github/workflows/ci.yml:54-56`

CI 中使用 `tools.tests.test_check_gpio` 和
`tools.fake_u1.tests.test_app` 等 Python 模块路径，
但 `tools/` 目录缺少 `__init__.py`，本地验证确认这些路径无法导入。

实际可运行的方式是：
- 直接执行：`rtk python tools/test_check_gpio.py`
- 从项目根目录 discover：`rtk python -m unittest discover -s tests/ci`

CI 在 GitHub Actions 上可能因路径差异而通过/失败不一致。

### 2.3 fake_u1 存在两套实现

- `tools/fake_u1/fake_u1.py` — 异步版（class `FakeU1`，用 asyncio）
- `tools/fake_u1/app.py` — 同步版（class `FakeU1Simulator`，用 socketserver）

两套实现的协议行为略有差异（如 PATH_SEG 参数格式），
`test_fake_u1.py` 测试的是异步版，CI 集成测试用的是同步版。
建议统一为一套，避免行为漂移。

### 2.4 platformio.ini 依赖版本范围过宽

```ini
lib_deps =
    TMCStepper@>=0.7.0,<1.0.0
```

建议锁定到具体版本（如 `@0.7.3`），避免上游破坏性更新。

---

## 3. 信息级问题（可选改进）

### 3.1 .gitignore 未排除 `.deps_pdf/`

仓库状态显示 `.deps_pdf/` 为 untracked，
但 `.gitignore` 中未列出。建议添加以避免误提交。

### 3.2 test_vectorize.py 缺少依赖声明

该文件依赖 `httpx`, `Pillow`, `numpy`, `scikit-image`，
但项目无 `requirements.txt` 或 `pyproject.toml` 声明 Python 依赖。
CI 中只安装了 `jsonschema` 和 `httpx`。

### 3.3 fake_device_server 全局变量修改

`tools/fake_device_server/app.py:353-354` 使用 `global` 修改模块级变量：

```python
global FAKE_U1_HOST, FAKE_U1_PORT
FAKE_U1_HOST = args.fake_u1_host
```

建议改为实例属性或配置对象传递。

### 3.4 CI 中 manager-api 测试类名硬编码

`.github/workflows/ci.yml:95` 中 `-Dtest=` 参数列出了 30+ 个测试类名，
维护成本高。建议改用 Maven profile 或 tag 分组。

---

## 4. 正面发现（自 v1 报告以来的改进）

1. **M0a 契约固化完成**：4 条边的 schema 全部落地，62 个校验项全绿
2. **M0b GPIO 检查器完成**：8 个单测 + 集成测试全绿
3. **M0c 仿真器三件套完成**：fake_u1 / fake_device_server / fake_ai 全部落地
4. **M0d CI 骨架完成**：7 个 job 覆盖 schema/GPIO/Python/集成/Java/Node/Markdown
5. **251 个测试全绿**：包括 M5/M6 里程碑的契约测试
6. **strapping pin 标注规范**：U1/U8 config.h 中所有 strapping pin 均有"已知风险"注释
7. **UART 交叉连接正确**：U8.IO11=TXD / U8.IO10=RXD 与硬件文档一致

---

## 5. 建议修复优先级

```
P0（立即）  §1.1 API 密钥泄露 — 轮换密钥 + 环境变量 + gitignore
P1（本周）  §2.1 bare except → 具体异常类型
P1（本周）  §2.2 CI 路径修复或添加 __init__.py
P2（下次）  §2.3 统一 fake_u1 两套实现
P2（下次）  §2.4 锁定 platformio 依赖版本
P3（可选）  §3.1-3.4 信息级改进
```

---

## 6. 与 v1 报告对比

| v1 报告项 | 当前状态 |
|-----------|----------|
| M0a 契约固化 ~25% | **100%** — 62 个 schema+example 全绿 |
| M0b GPIO 检查器 0% | **100%** — 工具 + 测试 + CI 集成 |
| M0c 仿真器 ~33% | **100%** — 三件套全部落地 |
| M0d CI 骨架 0% | **100%** — 7 个 job |
| Report.cpp dead code | 已修 |
| RunPath 超时 150ms | 已修（800ms） |
| task_id 写死 | 已修（动态生成） |
| login 信任客户端 unionid | 已修 |
| 弱密码硬编码 | 已修 |

**新发现**：API 密钥泄露（v1 未覆盖 test_vectorize.py）
