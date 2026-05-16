"""Tests for the M0b GPIO static checker."""

import sys
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from check_gpio import GPIOChecker, PinType, Severity


class TestGPIOCheckerRules(unittest.TestCase):
    def setUp(self):
        self.temp_dir = TemporaryDirectory()
        self.temp_path = Path(self.temp_dir.name)

    def tearDown(self):
        self.temp_dir.cleanup()

    def write_configs(self, u1_text: str, u8_text: str = ""):
        u1_config = self.temp_path / "u1_config.h"
        u8_config = self.temp_path / "u8_config.h"
        hardware_doc = self.temp_path / "hardware.md"
        u1_config.write_text(u1_text, encoding="utf-8")
        u8_config.write_text(u8_text, encoding="utf-8")
        hardware_doc.write_text("", encoding="utf-8")
        return u1_config, u8_config, hardware_doc

    def run_checker(self, u1_text: str, u8_text: str = ""):
        checker = GPIOChecker()
        return checker.check_all(*self.write_configs(u1_text, u8_text))

    def test_pin_type_inference(self):
        checker = GPIOChecker()
        self.assertEqual(checker._infer_pin_type("X_STEP_PIN"), PinType.OUTPUT)
        self.assertEqual(checker._infer_pin_type("LASER_CONTROL"), PinType.OUTPUT)
        self.assertEqual(checker._infer_pin_type("X_LIMIT_PIN"), PinType.INPUT)
        self.assertEqual(checker._infer_pin_type("U1_UART_RXD"), PinType.INPUT)
        self.assertEqual(checker._infer_pin_type("AUDIO_CODEC_I2C_SDA_PIN"), PinType.BIDIRECTIONAL)

    def test_duplicate_output_reports_line_number(self):
        issues = self.run_checker(
            """
#define FIRST_STEP_PIN GPIO_NUM_4
#define SECOND_STEP_PIN GPIO_NUM_4
"""
        )
        errors = [issue for issue in issues if issue.severity == Severity.ERROR]
        self.assertEqual(len(errors), 1)
        self.assertIn("GPIO4", errors[0].message)
        self.assertEqual(errors[0].line_number, 2)

    def test_strapping_output_without_known_risk_warns(self):
        issues = self.run_checker("#define X_STEP_PIN GPIO_NUM_46\n")
        warnings = [issue for issue in issues if issue.severity == Severity.WARNING]
        self.assertEqual(len(warnings), 1)
        self.assertIn("strapping pin", warnings[0].message)

    def test_strapping_output_with_known_risk_marker_passes(self):
        issues = self.run_checker("#define X_STEP_PIN GPIO_NUM_46\n// 已知风险：strapping pin\n")
        warnings = [issue for issue in issues if issue.severity == Severity.WARNING]
        self.assertEqual(warnings, [])

    def test_unavailable_pin_is_error(self):
        issues = self.run_checker("#define BAD_PIN GPIO_NUM_35\n")
        errors = [issue for issue in issues if issue.severity == Severity.ERROR]
        self.assertEqual(len(errors), 1)
        self.assertIn("未引出", errors[0].message)

    def test_u8_uart_swap_is_error(self):
        issues = self.run_checker(
            "",
            """
#define U1_UART_TXD GPIO_NUM_10
#define U1_UART_RXD GPIO_NUM_11
""",
        )
        errors = [issue for issue in issues if issue.severity == Severity.ERROR and "UART" in issue.message]
        self.assertEqual(len(errors), 2)


class TestGPIOCheckerRealFiles(unittest.TestCase):
    def test_real_config_files_pass(self):
        project_root = Path(__file__).resolve().parents[2]
        u1_config = project_root / "firmware/u1-grbl/Grbl_Esp32/src/Machines/dlc_motor_control_p1.h"
        u8_config = project_root / "firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/config.h"
        hardware_doc = project_root / "docs/硬件连接与GPIO分配说明.md"

        checker = GPIOChecker()
        issues = checker.check_all(u1_config, u8_config, hardware_doc)
        errors = [issue for issue in issues if issue.severity == Severity.ERROR]
        warnings = [issue for issue in issues if issue.severity == Severity.WARNING]

        self.assertEqual(errors, [])
        self.assertEqual(warnings, [])


if __name__ == "__main__":
    unittest.main()
