# M0b 实施计划：GPIO 静态检查器

**创建日期**: 2026-05-15
**状态**: 已完成
**目标**: 让 `tools/check_gpio.py` 覆盖 M0b 的 GPIO 冲突、strapping pin、UART 交叉和未引出引脚检查，并有可运行测试证明故意冲突能定位行号

## 0. 前置审计

- [x] 已读 `docs/实施计划-v2.md` M0b 完成判定。
- [x] 已确认 `tools/check_gpio.py` 已存在，不能重写。
- [x] 已读取 U1 配置 `firmware/u1-grbl/Grbl_Esp32/src/Machines/dlc_motor_control_p1.h`。
- [x] 已读取 U8 配置 `firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/config.h`。
- [x] 已读取硬件基准 `docs/硬件连接与GPIO分配说明.md` 和 M0g 风险表。

## 1. 发现的缺口

- [x] `tools/tests/test_check_gpio.py` 仍导入旧版函数 API，导致 `rtk python -m unittest tools.tests.test_check_gpio -v` 失败。
- [x] `GPIOChecker._infer_pin_type()` 未把 `LIMIT` 识别为输入。
- [x] I2C `SDA/SCL` 会被 `AUDIO` 名称中的 `DI` 误判为输入。
- [x] UART 交叉检查只有在 U1 侧也出现 UART 宏时才执行；真实守卫应独立检查 U8 `U1_UART_TXD=GPIO11`、`U1_UART_RXD=GPIO10`。
- [x] `tools/README.md` 中 schema validator 计数未随 M0a 更新。

## 2. 修改内容

- `tools/check_gpio.py`
  - `LIMIT` 归类为输入。
  - `SDA/SCL/DAT/CMD` 先于输入关键字判断，避免误判 I2C。
  - U8 UART TX/RX 固定引脚独立检查，不依赖 U1 侧宏是否存在。
- `tools/tests/test_check_gpio.py`
  - 更新到当前 `GPIOChecker` API。
  - 增加故意 duplicate output 时的行号断言。
  - 增加 U8 UART swap 检测断言。
  - 真实配置文件必须无 error/warning。
- `tools/README.md`
  - schema validator 期望输出更新为 `validated=57 passed=57 failed=0`。

## 3. 验证命令

```powershell
rtk python tools/check_gpio.py
rtk python tools/test_check_gpio.py -v
rtk python -m unittest tools.tests.test_check_gpio -v
rtk python tools/validate_schemas.py
```

验证结果：

- `tools/check_gpio.py`：`OK: GPIO check passed; no issues found`
- `tools/test_check_gpio.py -v`：8 tests OK
- `rtk python -m unittest tools.tests.test_check_gpio -v`：7 tests OK
- `tools/validate_schemas.py`：`validated=57 passed=57 failed=0`

## 4. 完成判定

- [x] 当前仓库跑通无错。
- [x] 故意加一处 OUTPUT 冲突，测试能定位到行号。
- [x] strapping pin 无已知风险标注时会报警，当前真实配置因已标注风险而无 warning。
- [x] U8 UART TX/RX swap 能被测试捕获。
- [x] 未引出 GPIO 能被测试捕获。
- [ ] commit/push 等待用户明确允许。

## 5. 修订记录

- 2026-05-15: 初始版本，记录 M0b 审计、修复和验证结果
