#!/usr/bin/env python3
"""
M0b: GPIO 冲突与 strapping pin 静态检查器.
对 U1 / U8 的 C header 做纯文本解析，对照硬件文档发现：
  1. 同一 MCU 内 GPIO 被重复定义为 OUTPUT
  2. ESP32-S3 strapping pin（IO0/IO3/IO45/IO46）被当作普通 OUTPUT
  3. UART TX/RX 命名规则：U8 TXD 必须 = 对端 U1 的 RXD
  4. N16R8 模组未引出引脚出现在 config 中
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# ESP32-S3 strapping pins
STRAPPING_PINS = {0, 3, 45, 46}
# N16R8 模组内部 Octal PSRAM 占用
UNEXPOSED_N16R8 = {35, 36, 37}

# U1 / U8 角色标记
MCU_ROLES = {
    "U1": "MOTOR_MCU",
    "U8": "AI_MCU",
}

# ------------------------------------------------------------
# 解析器
# ------------------------------------------------------------

def parse_gpio_defines(filepath: str) -> dict[str, tuple[int, str]]:
    """从 C header 提取所有 GPIO_NUM_xx 定义。返回 {宏名: (gpio_num, 行文本)}。"""
    defines: dict[str, tuple[int, str]] = {}
    pattern = re.compile(r'#define\s+(\w+)\s+GPIO_NUM_(\d+|NC)\b')
    with open(filepath, encoding='utf-8') as f:
        for line in f:
            m = pattern.search(line)
            if not m:
                continue
            name, num_str = m.group(1), m.group(2)
            if num_str == "NC":
                continue
            defines[name] = (int(num_str), line.strip())
    return defines


def classify_pin(name: str) -> str:
    """根据宏名推断 GPIO 方向。"""
    upper = name.upper()
    output_keywords = ["STEP", "DIR", "DIRECTION", "ENABLE", "DISABLE",
                       "SPINDLE", "LASER", "MCLK", "DOUT", "PA_EN", "PA_PIN",
                       "PWDN", "XCLK", "SIOD", "SIOC", "LED", "WS", "BCLK"]
    input_keywords = ["LIMIT", "PROBE", "DIN", "VSYNC", "HREF", "PCLK",
                      "BUTTON", "BOOT", "D0", "D1", "D2", "D3", "D4",
                      "D5", "D6", "D7"]
    uart_keywords = ["TXD", "RXD", "RTS", "CTS"]

    for kw in uart_keywords:
        if kw in upper:
            return "UART"
    for kw in output_keywords:
        if kw in upper:
            return "OUTPUT"
    for kw in input_keywords:
        if kw in upper:
            return "INPUT"
    if "SDA" in upper or "SCL" in upper:
        return "I2C"
    return "UNKNOWN"


# ------------------------------------------------------------
# 检查函数
# ------------------------------------------------------------

def check_duplicate_outputs(mcu_label: str, defines: dict[str, tuple[int, str]]):
    """同一 MCU 内同一 GPIO 被重复定义为 OUTPUT → 报错。"""
    gpio_to_names: dict[int, list[str]] = {}
    for name, (gpio, _) in defines.items():
        if classify_pin(name) == "OUTPUT":
            gpio_to_names.setdefault(gpio, []).append(name)

    errors = []
    for gpio, names in gpio_to_names.items():
        if len(names) > 1:
            errors.append(
                f"[{mcu_label}] GPIO{gpio} 重复 OUTPUT: {', '.join(names)}"
            )
    return errors


def check_strapping_pins(mcu_label: str, defines: dict[str, tuple[int, str]]):
    """strapping pin 被当作 OUTPUT 使用 → 报警。"""
    warnings = []
    for name, (gpio, line) in defines.items():
        if gpio in STRAPPING_PINS:
            direction = classify_pin(name)
            if direction == "OUTPUT":
                warnings.append(
                    f"[{mcu_label}] GPIO{gpio} 是 strapping pin，被定义为 OUTPUT（{name}）。"
                    f" 建议确认启动行为不受影响。"
                )
    return warnings


def check_tx_rx_swap(u8_defines: dict, u1_defines: dict):
    """U8 TXD 必须 = U1 RXD 的 GPIO 编号；U8 RXD 必须 = U1 TXD。"""
    errors = []
    u8_txd = _find_gpio(u8_defines, "TXD")
    u8_rxd = _find_gpio(u8_defines, "RXD")
    u1_txd = _find_gpio(u1_defines, "TXD")
    u1_rxd = _find_gpio(u1_defines, "RXD")

    # U1 的 UART 引脚不在 dlc_motor_control_p1.h 中定义，从 GPIO 文档补。
    # 文档 §1: U1.IO10=M_U1TXD → U8, U1.IO11=M_U1RXD ← U8
    if u1_txd is None:
        u1_txd = 10
    if u1_rxd is None:
        u1_rxd = 11

    if u8_txd is None or u1_rxd is None:
        errors.append("[UART] 无法解析 U8 TXD/RXD 定义，跳过交叉检查。")
        return errors

    if u8_txd != u1_rxd:
        errors.append(
            f"[UART] U8 TXD=GPIO{u8_txd} ≠ U1 RXD=GPIO{u1_rxd}。"
            f" 必需 U8.TX ↔ U1.RX 直连。"
        )
    if u8_rxd != u1_txd:
        errors.append(
            f"[UART] U8 RXD=GPIO{u8_rxd} ≠ U1 TXD=GPIO{u1_txd}。"
            f" 必需 U8.RX ↔ U1.TX 直连。"
        )
    return errors


def check_unexposed_pins(mcu_label: str, defines: dict[str, tuple[int, str]]):
    """N16R8 未引出引脚出现在 config → 报错。"""
    errors = []
    for name, (gpio, _) in defines.items():
        if gpio in UNEXPOSED_N16R8:
            errors.append(
                f"[{mcu_label}] GPIO{gpio}（{name}）在 N16R8 模组上为内部 PSRAM 占用，不可用。"
            )
    return errors


def _find_gpio(defines: dict, suffix: str) -> int | None:
    """查找名称包含 suffix 的宏，返回 GPIO 编号。"""
    for name, (gpio, _) in defines.items():
        if suffix in name.upper():
            return gpio
    return None


# ------------------------------------------------------------
# 主入口
# ------------------------------------------------------------

def main(argv: list[str] | None = None) -> int:
    u1_header = ROOT / "firmware/u1-grbl/Grbl_Esp32/src/Machines/dlc_motor_control_p1.h"
    u8_header = ROOT / "firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/config.h"

    missing = []
    for p in (u1_header, u8_header):
        if not p.exists():
            missing.append(str(p))
    if missing:
        print(f"ERROR: 缺少输入文件:\n  " + "\n  ".join(missing))
        return 1

    u1 = parse_gpio_defines(str(u1_header))
    u8 = parse_gpio_defines(str(u8_header))

    all_errors: list[str] = []
    all_warnings: list[str] = []

    # 检查项 1: 重复 OUTPUT
    all_errors.extend(check_duplicate_outputs("U1", u1))
    all_errors.extend(check_duplicate_outputs("U8", u8))

    # 检查项 2: strapping pin OUTPUT
    all_warnings.extend(check_strapping_pins("U1", u1))
    all_warnings.extend(check_strapping_pins("U8", u8))

    # 检查项 3: TX/RX swap
    all_errors.extend(check_tx_rx_swap(u8, u1))

    # 检查项 4: N16R8 未引出
    all_errors.extend(check_unexposed_pins("U1", u1))
    all_errors.extend(check_unexposed_pins("U8", u8))

    # 汇总输出
    if all_errors:
        print(f"\n=== GPIO 检查错误 ({len(all_errors)}) ===")
        for e in all_errors:
            print(f"  ERROR: {e}")

    if all_warnings:
        print(f"\n=== GPIO 检查警告 ({len(all_warnings)}) ===")
        for w in all_warnings:
            print(f"  WARN:  {w}")

    if not all_errors and not all_warnings:
        print("GPIO 检查通过：无冲突、无 strapping 误用、UART 交叉正确。")

    # 额外：打印已解析的 GPIO 汇总
    print(f"\n--- 已解析 U1 GPIO ({len(u1)} 项) ---")
    for name, (gpio, _) in sorted(u1.items(), key=lambda x: x[1][0]):
        print(f"  {name:30s} GPIO{gpio:>3}  [{classify_pin(name)}]")

    print(f"\n--- 已解析 U8 GPIO ({len(u8)} 项) ---")
    for name, (gpio, _) in sorted(u8.items(), key=lambda x: x[1][0]):
        print(f"  {name:30s} GPIO{gpio:>3}  [{classify_pin(name)}]")

    return 0 if not all_errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
