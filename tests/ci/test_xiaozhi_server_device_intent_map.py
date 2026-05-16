import sys
import unittest
from pathlib import Path


SERVER_ROOT = Path(__file__).resolve().parents[2] / "server" / "xiaozhi-esp32-server" / "main" / "xiaozhi-server"
sys.path.insert(0, str(SERVER_ROOT))

from core.handle.deviceIntentMap import resolve_direct_device_tool, resolve_voice_task_intent  # noqa: E402


class DeviceIntentMapTests(unittest.TestCase):
    def test_device_info_question_maps_to_device_info_tool(self):
        intent = resolve_direct_device_tool(
            "\u8fd9\u53f0\u673a\u5668\u662f\u4ec0\u4e48\u578b\u53f7",
            lambda name: name == "self.motor.get_device_info",
        )

        self.assertEqual(intent["function_call"]["name"], "self.motor.get_device_info")
        self.assertEqual(intent["function_call"]["arguments"], {})

    def test_home_phrase_maps_to_home_tool(self):
        intent = resolve_direct_device_tool(
            "\u8bf7\u56de\u539f\u70b9",
            lambda name: name == "self.motor.home",
        )

        self.assertEqual(intent["function_call"]["name"], "self.motor.home")

    def test_pause_resume_stop_phrases_map_to_control_tools(self):
        expected = {
            "\u6682\u505c\u4e00\u4e0b": "self.motor.pause",
            "\u7ee7\u7eed\u8fd0\u884c": "self.motor.resume",
            "\u505c\u4e0b": "self.motor.stop",
        }

        for text, tool_name in expected.items():
            with self.subTest(text=text):
                intent = resolve_direct_device_tool(text, lambda name: name == tool_name)
                self.assertEqual(intent["function_call"]["name"], tool_name)

    def test_small_relative_move_phrases_map_to_whitelisted_move_rel(self):
        expected = {
            "\u5f80\u5de6\u4e00\u70b9": {"dx": -1, "dy": 0, "dz": 0, "feed": 800},
            "\u5f80\u53f3\u4e00\u70b9": {"dx": 1, "dy": 0, "dz": 0, "feed": 800},
            "\u5f80\u4e0a\u4e00\u70b9": {"dx": 0, "dy": 0, "dz": 1, "feed": 800},
            "\u964d\u4f4e\u4e00\u70b9": {"dx": 0, "dy": 0, "dz": -1, "feed": 800},
        }

        for text, arguments in expected.items():
            with self.subTest(text=text):
                intent = resolve_direct_device_tool(text, lambda name: name == "self.motor.move_rel")
                self.assertEqual(intent["function_call"]["name"], "self.motor.move_rel")
                self.assertEqual(intent["function_call"]["arguments"], arguments)

    def test_small_relative_move_requires_registered_tool(self):
        self.assertIsNone(resolve_direct_device_tool("\u5f80\u5de6\u4e00\u70b9", lambda name: False))

    def test_missing_registered_tool_does_not_intercept(self):
        self.assertIsNone(resolve_direct_device_tool("\u8fd9\u53f0\u673a\u5668\u662f\u4ec0\u4e48\u578b\u53f7", lambda name: False))

    def test_unrelated_text_does_not_intercept(self):
        self.assertIsNone(resolve_direct_device_tool("\u4eca\u5929\u5929\u6c14\u600e\u4e48\u6837", lambda name: True))

    def test_voice_write_text_maps_to_source_voice_task(self):
        intent = resolve_voice_task_intent("\u5c0f\u667a\uff0c\u5199\u4f60\u597d")

        self.assertEqual(intent["task"]["capability"], "write_text")
        self.assertEqual(intent["task"]["source"], "voice")
        self.assertEqual(intent["task"]["params"], {"text": "\u4f60\u597d", "font_id": "kai_basic_v1"})

    def test_voice_write_text_supports_soft_prefix(self):
        intent = resolve_voice_task_intent("\u8bf7\u5e2e\u6211\u5199\u4e00\u4e0b\u4f60\u597d")

        self.assertEqual(intent["task"]["capability"], "write_text")
        self.assertEqual(intent["task"]["params"]["text"], "\u4f60\u597d")

    def test_voice_draw_generated_maps_to_source_voice_task(self):
        intent = resolve_voice_task_intent("\u5c0f\u667a\u753b\u4e00\u4e2a\u661f\u661f")

        self.assertEqual(intent["task"]["capability"], "draw_generated")
        self.assertEqual(intent["task"]["source"], "voice")
        self.assertEqual(intent["task"]["params"], {"prompt": "\u661f\u661f"})

    def test_voice_task_does_not_match_control_phrase(self):
        self.assertIsNone(resolve_voice_task_intent("\u5f80\u5de6\u4e00\u70b9"))


if __name__ == "__main__":
    unittest.main()
