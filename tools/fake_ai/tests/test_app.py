import json
import io
import unittest
import wave
from pathlib import Path

from jsonschema import Draft202012Validator

from tools.fake_ai.app import build_arg_parser, fake_asr, fake_intent, fake_tts_wav


ROOT = Path(__file__).resolve().parents[3]
INTENT_SCHEMA = json.loads((ROOT / "docs" / "schemas" / "edge_b" / "intent_submit.schema.json").read_text(encoding="utf-8-sig"))


class FakeAITests(unittest.TestCase):
    def setUp(self) -> None:
        self.intent_validator = Draft202012Validator(INTENT_SCHEMA)

    def assert_intent_schema_valid(self, payload: dict) -> None:
        errors = sorted(self.intent_validator.iter_errors(payload), key=lambda error: list(error.path))
        self.assertEqual(errors, [], [error.message for error in errors])

    def test_asr_default(self):
        data = fake_asr()
        self.assertEqual(data["type"], "asr")
        self.assertTrue(data["transcript"])

    def test_device_intent_home_is_edge_b_shape(self):
        data = fake_intent("小智回原点", device_id="dev1", session_id="sess1")
        self.assertEqual(data["intent"], "home")
        self.assertEqual(data["device_id"], "dev1")
        self.assertEqual(data["session_id"], "sess1")
        self.assert_intent_schema_valid(data)

    def test_device_intent_move_relative(self):
        data = fake_intent("往左一点", device_id="dev1", session_id="sess2")
        self.assertEqual(data["intent"], "move_relative")
        self.assertEqual(data["params"]["dx"], -1.0)
        self.assert_intent_schema_valid(data)

    def test_chat_text_has_no_device_intent(self):
        data = fake_intent("今天天气怎么样", device_id="dev1", session_id="sess3")
        self.assertNotIn("intent", data)
        self.assert_intent_schema_valid(data)

    def test_tts_wav_is_valid_pcm(self):
        wav_bytes = fake_tts_wav("hello", duration_ms=50)
        with wave.open(io.BytesIO(wav_bytes), "rb") as wav:
            self.assertEqual(wav.getnchannels(), 1)
            self.assertEqual(wav.getframerate(), 16000)
            self.assertGreater(wav.getnframes(), 0)

    def test_parser_requires_subcommand(self):
        parser = build_arg_parser()
        args = parser.parse_args(["intent", "--text", "home"])
        self.assertEqual(args.command, "intent")


if __name__ == "__main__":
    unittest.main()
