"""M0b GPIO checker 单元测试。"""
import unittest
from pathlib import Path
import sys

# 确保能 import check_gpio
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from check_gpio import parse_gpio_defines, classify_pin, check_duplicate_outputs, check_strapping_pins, check_unexposed_pins


class TestClassify(unittest.TestCase):
    def test_step_is_output(self):
        self.assertEqual(classify_pin("X_STEP_PIN"), "OUTPUT")

    def test_limit_is_input(self):
        self.assertEqual(classify_pin("X_LIMIT_PIN"), "INPUT")

    def test_uart_is_uart(self):
        self.assertEqual(classify_pin("U1_UART_TXD"), "UART")

    def test_unknown(self):
        self.assertEqual(classify_pin("SOME_RANDOM"), "UNKNOWN")


class TestDuplicateOutputs(unittest.TestCase):
    def test_no_duplicate(self):
        defines = {
            "X_STEP": (5, ""),
            "Y_STEP": (8, ""),
        }
        errors = check_duplicate_outputs("TEST", defines)
        self.assertEqual(len(errors), 0)

    def test_duplicate(self):
        defines = {
            "A_STEP": (5, ""),
            "B_STEP": (5, ""),
        }
        errors = check_duplicate_outputs("TEST", defines)
        self.assertEqual(len(errors), 1)
        self.assertIn("GPIO5", errors[0])


class TestStrapping(unittest.TestCase):
    def test_strapping_as_output_warns(self):
        defines = {"X_DIR": (0, "")}  # IO0 is strapping
        warnings = check_strapping_pins("TEST", defines)
        self.assertEqual(len(warnings), 1)

    def test_strapping_as_input_no_warn(self):
        defines = {"X_LIMIT": (3, "")}  # IO3 strapping but INPUT
        warnings = check_strapping_pins("TEST", defines)
        self.assertEqual(len(warnings), 0)


class TestUnexposed(unittest.TestCase):
    def test_unexposed_detected(self):
        defines = {"BAD_PIN": (35, "")}
        errors = check_unexposed_pins("TEST", defines)
        self.assertEqual(len(errors), 1)

    def test_valid_pin_ok(self):
        defines = {"OK_PIN": (4, "")}
        errors = check_unexposed_pins("TEST", defines)
        self.assertEqual(len(errors), 0)


if __name__ == "__main__":
    unittest.main()
