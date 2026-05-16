#!/usr/bin/env python3
"""
GPIO 静态检查工具

检查项：
1. 同一 GPIO 在同一 MCU 内被多处定义为 OUTPUT
2. ESP32-S3 strapping pin（IO0/IO3/IO45/IO46）被当作普通 OUTPUT 使用
3. TX/RX 命名规则：U8 侧 TXD 必须 = 对端 U1 的 RXD
4. 模组未引出引脚（IO35~IO42 在 N16R8 模组上）出现在 config

依据：
- 实施计划 v2 M0b
- 硬件连接与GPIO分配说明.md
"""

import re
import sys
from pathlib import Path
from typing import Dict, List, Set, Tuple
from dataclasses import dataclass
from enum import Enum


class PinType(Enum):
    """GPIO 引脚类型"""
    INPUT = "INPUT"
    OUTPUT = "OUTPUT"
    BIDIRECTIONAL = "BIDIRECTIONAL"
    UNKNOWN = "UNKNOWN"


class Severity(Enum):
    """问题严重程度"""
    ERROR = "ERROR"      # 必须修复
    WARNING = "WARNING"  # 建议修复
    INFO = "INFO"        # 信息提示


@dataclass
class GPIODefinition:
    """GPIO 定义"""
    mcu: str           # U1 或 U8
    gpio: int          # GPIO 编号
    signal_name: str   # 信号名称
    pin_type: PinType  # 引脚类型
    file_path: str     # 文件路径
    line_number: int   # 行号
    line_content: str  # 行内容


@dataclass
class Issue:
    """检查发现的问题"""
    severity: Severity
    message: str
    file_path: str
    line_number: int
    gpio: int = None
    mcu: str = None


class GPIOChecker:
    """GPIO 静态检查器"""
    
    # ESP32-S3 strapping pins
    STRAPPING_PINS = {0, 3, 45, 46}
    
    # ESP32-S3-WROOM-1-N16R8 模组未引出引脚
    # 仅 IO35/36/37/41/42 确认不可用
    UNAVAILABLE_PINS = {35, 36, 37, 41, 42}
    
    # Weak-evidence pins. The 2026-05-14 PADS Logic .txt export proves
    # U8 IO38/IO39/IO40 signal-to-pin mapping, so none are listed now.
    WEAK_EVIDENCE_PINS = set()
    
    def __init__(self):
        self.gpio_definitions: List[GPIODefinition] = []
        self.issues: List[Issue] = []
    
    def check_all(self, u1_config: Path, u8_config: Path, hardware_doc: Path) -> List[Issue]:
        """
        执行所有检查
        
        Args:
            u1_config: U1 配置文件路径
            u8_config: U8 配置文件路径
            hardware_doc: 硬件文档路径
        
        Returns:
            发现的问题列表
        """
        # 解析配置文件
        self._parse_u1_config(u1_config)
        self._parse_u8_config(u8_config)
        
        # 执行检查
        self._check_gpio_conflicts()
        self._check_strapping_pins()
        self._check_uart_crossover()
        self._check_unavailable_pins()
        self._check_weak_evidence_pins()
        
        return self.issues
    
    def _parse_u1_config(self, config_path: Path):
        """解析 U1 配置文件"""
        if not config_path.exists():
            self.issues.append(Issue(
                severity=Severity.ERROR,
                message=f"U1 配置文件不存在: {config_path}",
                file_path=str(config_path),
                line_number=0
            ))
            return
        
        content = config_path.read_text(encoding='utf-8')
        lines = content.split('\n')
        
        # 匹配 GPIO 定义模式
        # 例如：#define MOTOR_EN_PIN GPIO_NUM_4
        gpio_pattern = re.compile(r'#define\s+(\w+)\s+GPIO_NUM_(\d+)')
        
        for line_num, line in enumerate(lines, 1):
            match = gpio_pattern.search(line)
            if match:
                signal_name = match.group(1)
                gpio_num = int(match.group(2))
                
                # 推断引脚类型
                pin_type = self._infer_pin_type(signal_name)
                
                self.gpio_definitions.append(GPIODefinition(
                    mcu="U1",
                    gpio=gpio_num,
                    signal_name=signal_name,
                    pin_type=pin_type,
                    file_path=str(config_path),
                    line_number=line_num,
                    line_content=line.strip()
                ))
    
    def _parse_u8_config(self, config_path: Path):
        """解析 U8 配置文件"""
        if not config_path.exists():
            self.issues.append(Issue(
                severity=Severity.ERROR,
                message=f"U8 配置文件不存在: {config_path}",
                file_path=str(config_path),
                line_number=0
            ))
            return
        
        content = config_path.read_text(encoding='utf-8')
        lines = content.split('\n')
        
        # 匹配 GPIO 定义模式
        gpio_pattern = re.compile(r'#define\s+(\w+)\s+(?:GPIO_NUM_)?(\d+)')
        
        for line_num, line in enumerate(lines, 1):
            # 跳过注释行
            if line.strip().startswith('//'):
                continue
            
            match = gpio_pattern.search(line)
            if match:
                signal_name = match.group(1)
                gpio_num = int(match.group(2))
                
                # 推断引脚类型
                pin_type = self._infer_pin_type(signal_name)
                
                self.gpio_definitions.append(GPIODefinition(
                    mcu="U8",
                    gpio=gpio_num,
                    signal_name=signal_name,
                    pin_type=pin_type,
                    file_path=str(config_path),
                    line_number=line_num,
                    line_content=line.strip()
                ))
    
    def _infer_pin_type(self, signal_name: str) -> PinType:
        """根据信号名称推断引脚类型"""
        signal_upper = signal_name.upper()
        
        # BIDIRECTIONAL 信号先检查，避免 AUDIO/CODEC 等名称里的 "DI" 误伤 I2C_SDA/SCL。
        bidir_keywords = ['SDA', 'SCL', 'DAT', 'CMD']
        if any(kw in signal_upper for kw in bidir_keywords):
            return PinType.BIDIRECTIONAL

        # INPUT 信号
        input_keywords = ['SENSOR', 'LIMIT', 'RXD', 'RX', 'MISO', 'DI', 'INPUT']
        if any(kw in signal_upper for kw in input_keywords):
            return PinType.INPUT
        
        # OUTPUT 信号
        output_keywords = ['STEP', 'DIR', 'EN', 'PWM', 'CONTROL', 'SCLK', 'TXD', 'TX', 'OUTPUT']
        if any(kw in signal_upper for kw in output_keywords):
            return PinType.OUTPUT
        
        return PinType.UNKNOWN
    
    def _check_gpio_conflicts(self):
        """检查 GPIO 冲突（同一 GPIO 被多处定义为 OUTPUT）"""
        # 按 MCU 分组
        u1_outputs: Dict[int, List[GPIODefinition]] = {}
        u8_outputs: Dict[int, List[GPIODefinition]] = {}
        
        for defn in self.gpio_definitions:
            if defn.pin_type == PinType.OUTPUT:
                if defn.mcu == "U1":
                    u1_outputs.setdefault(defn.gpio, []).append(defn)
                elif defn.mcu == "U8":
                    u8_outputs.setdefault(defn.gpio, []).append(defn)
        
        # 检查 U1 冲突
        for gpio, defns in u1_outputs.items():
            if len(defns) > 1:
                signal_names = [d.signal_name for d in defns]
                self.issues.append(Issue(
                    severity=Severity.ERROR,
                    message=f"U1 GPIO{gpio} 被多处定义为 OUTPUT: {', '.join(signal_names)}",
                    file_path=defns[0].file_path,
                    line_number=defns[0].line_number,
                    gpio=gpio,
                    mcu="U1"
                ))
        
        # 检查 U8 冲突
        for gpio, defns in u8_outputs.items():
            if len(defns) > 1:
                signal_names = [d.signal_name for d in defns]
                self.issues.append(Issue(
                    severity=Severity.ERROR,
                    message=f"U8 GPIO{gpio} 被多处定义为 OUTPUT: {', '.join(signal_names)}",
                    file_path=defns[0].file_path,
                    line_number=defns[0].line_number,
                    gpio=gpio,
                    mcu="U8"
                ))
    
    def _check_strapping_pins(self):
        """检查 strapping pin 误用"""
        for defn in self.gpio_definitions:
            if defn.gpio in self.STRAPPING_PINS and defn.pin_type == PinType.OUTPUT:
                # 检查是否有"已知风险"标注（检查当前行和下一行的注释）
                has_known_risk = (
                    "KNOWN_RISK" in defn.line_content or 
                    "known risk" in defn.line_content.lower() or
                    "已知风险" in defn.line_content
                )
                
                if not has_known_risk:
                    # 读取文件检查下一行是否有注释
                    try:
                        with open(defn.file_path, 'r', encoding='utf-8') as f:
                            lines = f.readlines()
                            if defn.line_number < len(lines):
                                next_line = lines[defn.line_number].strip()  # line_number 是 1-based，所以 +0 就是下一行
                                if ("KNOWN_RISK" in next_line or 
                                    "known risk" in next_line.lower() or
                                    "已知风险" in next_line):
                                    has_known_risk = True
                    except (OSError, IndexError):
                        pass
                
                if not has_known_risk:
                    self.issues.append(Issue(
                        severity=Severity.WARNING,
                        message=f"{defn.mcu} GPIO{defn.gpio} 是 strapping pin，被用作 OUTPUT ({defn.signal_name})，需要显式标注'已知风险'",
                        file_path=defn.file_path,
                        line_number=defn.line_number,
                        gpio=defn.gpio,
                        mcu=defn.mcu
                    ))
    
    def _check_uart_crossover(self):
        """检查 UART TX/RX 交叉连接"""
        # 查找 U8 和 U1 的 UART 定义
        u8_uart_tx = None
        u8_uart_rx = None
        u1_uart_tx = None
        u1_uart_rx = None
        
        for defn in self.gpio_definitions:
            signal_upper = defn.signal_name.upper()
            
            if defn.mcu == "U8":
                if 'U1' in signal_upper and 'TXD' in signal_upper:
                    u8_uart_tx = defn
                elif 'U1' in signal_upper and 'RXD' in signal_upper:
                    u8_uart_rx = defn
            
            elif defn.mcu == "U1":
                if 'U1' in signal_upper and 'TXD' in signal_upper:
                    u1_uart_tx = defn
                elif 'U1' in signal_upper and 'RXD' in signal_upper:
                    u1_uart_rx = defn
        
        # 验证交叉连接。硬件基准固定为：
        # U8 GPIO11 = M_U1TXD -> U1 RXD, U8 GPIO10 = M_U1RXD <- U1 TXD.
        # U1 固件侧可能不显式声明 UART1 pin，因此 U8 侧一旦出现就独立检查。
        if u8_uart_tx:
            if u8_uart_tx.gpio != 11:  # 根据硬件文档，U8.IO11 = M_U1TXD
                self.issues.append(Issue(
                    severity=Severity.ERROR,
                    message=f"U8 UART TX 配置错误：应该是 GPIO11，当前是 GPIO{u8_uart_tx.gpio}",
                    file_path=u8_uart_tx.file_path,
                    line_number=u8_uart_tx.line_number,
                    gpio=u8_uart_tx.gpio,
                    mcu="U8"
                ))
        
        if u8_uart_rx:
            if u8_uart_rx.gpio != 10:  # 根据硬件文档，U8.IO10 = M_U1RXD
                self.issues.append(Issue(
                    severity=Severity.ERROR,
                    message=f"U8 UART RX 配置错误：应该是 GPIO10，当前是 GPIO{u8_uart_rx.gpio}",
                    file_path=u8_uart_rx.file_path,
                    line_number=u8_uart_rx.line_number,
                    gpio=u8_uart_rx.gpio,
                    mcu="U8"
                ))
    
    def _check_unavailable_pins(self):
        """检查模组未引出引脚误用"""
        for defn in self.gpio_definitions:
            if defn.gpio in self.UNAVAILABLE_PINS:
                self.issues.append(Issue(
                    severity=Severity.ERROR,
                    message=f"{defn.mcu} GPIO{defn.gpio} 在 ESP32-S3-WROOM-1-N16R8 模组上未引出，不可使用 ({defn.signal_name})",
                    file_path=defn.file_path,
                    line_number=defn.line_number,
                    gpio=defn.gpio,
                    mcu=defn.mcu
                ))
    
    def _check_weak_evidence_pins(self):
        """检查仍缺少设计源文件证据的引脚"""
        for defn in self.gpio_definitions:
            if defn.gpio in self.WEAK_EVIDENCE_PINS:
                self.issues.append(Issue(
                    severity=Severity.INFO,
                    message=f"{defn.mcu} GPIO{defn.gpio} 缺少 PADS 源文件独立证据，需要实机验证 ({defn.signal_name})",
                    file_path=defn.file_path,
                    line_number=defn.line_number,
                    gpio=defn.gpio,
                    mcu=defn.mcu
                ))
    
    def print_report(self):
        """打印检查报告"""
        if not self.issues:
            print("OK: GPIO check passed; no issues found")
            return
        
        # 按严重程度分组
        errors = [i for i in self.issues if i.severity == Severity.ERROR]
        warnings = [i for i in self.issues if i.severity == Severity.WARNING]
        infos = [i for i in self.issues if i.severity == Severity.INFO]
        
        print(f"\n{'='*80}")
        print("GPIO static check report")
        print(f"{'='*80}\n")
        
        if errors:
            print(f"ERRORS ({len(errors)}):")
            for issue in errors:
                print(f"  [{issue.mcu or 'N/A'}] {issue.file_path}:{issue.line_number}")
                print(f"      {issue.message}\n")
        
        if warnings:
            print(f"WARNINGS ({len(warnings)}):")
            for issue in warnings:
                print(f"  [{issue.mcu or 'N/A'}] {issue.file_path}:{issue.line_number}")
                print(f"      {issue.message}\n")
        
        if infos:
            print(f"INFO ({len(infos)}):")
            for issue in infos:
                print(f"  [{issue.mcu or 'N/A'}] {issue.file_path}:{issue.line_number}")
                print(f"      {issue.message}\n")
        
        print(f"{'='*80}")
        print(f"Total: {len(errors)} errors, {len(warnings)} warnings, {len(infos)} info")
        print(f"{'='*80}\n")


def main():
    """主函数"""
    # 获取项目根目录
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    
    # 配置文件路径
    u1_config = project_root / "firmware/u1-grbl/Grbl_Esp32/src/Machines/dlc_motor_control_p1.h"
    u8_config = project_root / "firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/config.h"
    hardware_doc = project_root / "docs/硬件连接与GPIO分配说明.md"
    
    # 创建检查器
    checker = GPIOChecker()
    
    # 执行检查
    issues = checker.check_all(u1_config, u8_config, hardware_doc)
    
    # 打印报告
    checker.print_report()
    
    # 返回退出码
    errors = [i for i in issues if i.severity == Severity.ERROR]
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
