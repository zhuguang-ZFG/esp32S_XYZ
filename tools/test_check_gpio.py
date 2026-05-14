#!/usr/bin/env python3
"""
GPIO 检查工具的单元测试

遵循 TDD 原则：先写测试，再实现功能
"""

import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from check_gpio import GPIOChecker, GPIODefinition, PinType, Severity, Issue


class TestGPIOChecker(unittest.TestCase):
    """GPIO 检查器测试"""
    
    def setUp(self):
        """测试前准备"""
        self.checker = GPIOChecker()
        self.temp_dir = TemporaryDirectory()
        self.temp_path = Path(self.temp_dir.name)
    
    def tearDown(self):
        """测试后清理"""
        self.temp_dir.cleanup()
    
    def test_no_conflicts_passes(self):
        """测试：无冲突时检查通过"""
        # 创建测试配置文件
        u1_config = self.temp_path / "u1_config.h"
        u1_config.write_text("""
#define MOTOR_EN_PIN GPIO_NUM_4
#define STEP_PIN GPIO_NUM_5
#define DIR_PIN GPIO_NUM_6
        """)
        
        u8_config = self.temp_path / "u8_config.h"
        u8_config.write_text("""
#define DVP_D0 GPIO_NUM_16
#define DVP_D1 GPIO_NUM_18
        """)
        
        hardware_doc = self.temp_path / "hardware.md"
        hardware_doc.write_text("")
        
        # 执行检查
        issues = self.checker.check_all(u1_config, u8_config, hardware_doc)
        
        # 验证：无错误
        errors = [i for i in issues if i.severity == Severity.ERROR]
        self.assertEqual(len(errors), 0)
    
    def test_gpio_conflict_detected(self):
        """测试：检测到 GPIO 冲突"""
        # 创建有冲突的配置
        u1_config = self.temp_path / "u1_config.h"
        u1_config.write_text("""
#define MOTOR_EN_PIN GPIO_NUM_4
#define LASER_CONTROL GPIO_NUM_4
        """)
        
        u8_config = self.temp_path / "u8_config.h"
        u8_config.write_text("")
        
        hardware_doc = self.temp_path / "hardware.md"
        hardware_doc.write_text("")
        
        # 执行检查
        issues = self.checker.check_all(u1_config, u8_config, hardware_doc)
        
        # 验证：发现错误
        errors = [i for i in issues if i.severity == Severity.ERROR]
        self.assertGreater(len(errors), 0)
        
        # 验证：错误消息包含 GPIO4
        error_messages = [i.message for i in errors]
        self.assertTrue(any("GPIO4" in msg for msg in error_messages))
    
    def test_strapping_pin_warning(self):
        """测试：strapping pin 使用警告"""
        # 创建使用 strapping pin 的配置
        u1_config = self.temp_path / "u1_config.h"
        u1_config.write_text("""
#define STEP_PIN GPIO_NUM_46
        """)
        
        u8_config = self.temp_path / "u8_config.h"
        u8_config.write_text("")
        
        hardware_doc = self.temp_path / "hardware.md"
        hardware_doc.write_text("")
        
        # 执行检查
        issues = self.checker.check_all(u1_config, u8_config, hardware_doc)
        
        # 验证：发现警告
        warnings = [i for i in issues if i.severity == Severity.WARNING]
        self.assertGreater(len(warnings), 0)
        
        # 验证：警告消息包含 strapping pin
        warning_messages = [i.message for i in warnings]
        self.assertTrue(any("strapping pin" in msg for msg in warning_messages))
    
    def test_strapping_pin_with_known_risk_no_warning(self):
        """测试：标注已知风险的 strapping pin 不警告"""
        # 创建标注已知风险的配置
        u1_config = self.temp_path / "u1_config.h"
        u1_config.write_text("""
#define STEP_PIN GPIO_NUM_46  // KNOWN_RISK: strapping pin
        """)
        
        u8_config = self.temp_path / "u8_config.h"
        u8_config.write_text("")
        
        hardware_doc = self.temp_path / "hardware.md"
        hardware_doc.write_text("")
        
        # 执行检查
        issues = self.checker.check_all(u1_config, u8_config, hardware_doc)
        
        # 验证：无警告
        warnings = [i for i in issues if i.severity == Severity.WARNING]
        self.assertEqual(len(warnings), 0)
    
    def test_unavailable_pin_error(self):
        """测试：检测模组未引出引脚"""
        # 创建使用未引出引脚的配置
        u1_config = self.temp_path / "u1_config.h"
        u1_config.write_text("""
#define SOME_PIN GPIO_NUM_35
        """)
        
        u8_config = self.temp_path / "u8_config.h"
        u8_config.write_text("")
        
        hardware_doc = self.temp_path / "hardware.md"
        hardware_doc.write_text("")
        
        # 执行检查
        issues = self.checker.check_all(u1_config, u8_config, hardware_doc)
        
        # 验证：发现错误
        errors = [i for i in issues if i.severity == Severity.ERROR]
        self.assertGreater(len(errors), 0)
        
        # 验证：错误消息包含"未引出"
        error_messages = [i.message for i in errors]
        self.assertTrue(any("未引出" in msg for msg in error_messages))
    
    def test_uart_crossover_correct(self):
        """测试：正确的 UART 交叉连接"""
        # 创建正确的 UART 配置
        u1_config = self.temp_path / "u1_config.h"
        u1_config.write_text("""
#define M_U1TXD GPIO_NUM_10
#define M_U1RXD GPIO_NUM_11
        """)
        
        u8_config = self.temp_path / "u8_config.h"
        u8_config.write_text("""
#define M_U1TXD 11
#define M_U1RXD 10
        """)
        
        hardware_doc = self.temp_path / "hardware.md"
        hardware_doc.write_text("")
        
        # 执行检查
        issues = self.checker.check_all(u1_config, u8_config, hardware_doc)
        
        # 验证：无 UART 相关错误
        uart_errors = [i for i in issues if i.severity == Severity.ERROR and "UART" in i.message]
        self.assertEqual(len(uart_errors), 0)
    
    def test_pin_type_inference(self):
        """测试：引脚类型推断"""
        # OUTPUT 信号
        self.assertEqual(self.checker._infer_pin_type("MOTOR_STEP"), PinType.OUTPUT)
        self.assertEqual(self.checker._infer_pin_type("LASER_CONTROL"), PinType.OUTPUT)
        self.assertEqual(self.checker._infer_pin_type("PWM_PIN"), PinType.OUTPUT)
        
        # INPUT 信号
        self.assertEqual(self.checker._infer_pin_type("SENSOR_INPUT"), PinType.INPUT)
        self.assertEqual(self.checker._infer_pin_type("UART_RXD"), PinType.INPUT)
        
        # BIDIRECTIONAL 信号
        self.assertEqual(self.checker._infer_pin_type("I2C_SDA"), PinType.BIDIRECTIONAL)
        self.assertEqual(self.checker._infer_pin_type("I2C_SCL"), PinType.BIDIRECTIONAL)


class TestIntegration(unittest.TestCase):
    """集成测试：使用真实配置文件"""
    
    def test_real_config_files(self):
        """测试：真实配置文件检查"""
        # 获取项目根目录
        script_dir = Path(__file__).parent
        project_root = script_dir.parent
        
        # 配置文件路径
        u1_config = project_root / "firmware/u1-grbl/Grbl_Esp32/src/Machines/dlc_motor_control_p1.h"
        u8_config = project_root / "firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/config.h"
        hardware_doc = project_root / "docs/硬件连接与GPIO分配说明.md"
        
        # 如果文件不存在，跳过测试
        if not u1_config.exists() or not u8_config.exists():
            self.skipTest("配置文件不存在")
        
        # 创建检查器
        checker = GPIOChecker()
        
        # 执行检查
        issues = checker.check_all(u1_config, u8_config, hardware_doc)
        
        # 打印报告（用于调试）
        checker.print_report()
        
        # 验证：至少能执行完成（不崩溃）
        self.assertIsInstance(issues, list)


if __name__ == "__main__":
    unittest.main()
