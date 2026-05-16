import asyncio
import sys
import unittest
from pathlib import Path
from types import ModuleType
from types import SimpleNamespace


SERVER_ROOT = Path(__file__).resolve().parents[2] / "server" / "xiaozhi-esp32-server" / "main" / "xiaozhi-server"
sys.path.insert(0, str(SERVER_ROOT))


class FakeLogger:
    def bind(self, **kwargs):
        return self

    def debug(self, *args, **kwargs):
        pass

    def info(self, *args, **kwargs):
        pass

    def warning(self, *args, **kwargs):
        pass


logger_module = ModuleType("config.logger")
logger_module.setup_logging = lambda: FakeLogger()
sys.modules.setdefault("config.logger", logger_module)

from core.handle.textHandler.motionEventMessageHandler import (  # noqa: E402
    MotionEventTextMessageHandler,
    _business_payload,
    _business_result_error,
    motion_event_tts_hint,
)


class FakeResponse:
    status_code = 200
    text = "ok"


class FakeAsyncClient:
    calls = []

    def __init__(self, timeout):
        self.timeout = timeout

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb):
        return False

    async def post(self, url, json, headers):
        self.calls.append({"url": url, "json": json, "headers": headers, "timeout": self.timeout})
        return FakeResponse()


class FakeTTS:
    def __init__(self):
        self.sentences = []
        self.stored = []

    def tts_one_sentence(self, conn, content_type, content_detail=None, content_file=None, sentence_id=None):
        self.sentences.append({"content_type": content_type, "content_detail": content_detail})

    def store_tts_text(self, sentence_id, text):
        self.stored.append({"sentence_id": sentence_id, "text": text})


class MotionEventTtsHintTests(unittest.TestCase):
    def test_business_payload_strips_transport_fields(self):
        payload = _business_payload(
            {
                "type": "motion_event",
                "session_id": "session-1",
                "task_id": "task-1",
                "phase": "done",
            }
        )

        self.assertEqual(payload, {"task_id": "task-1", "phase": "done"})

    def test_business_result_error_detects_rejected_motion_event_forwarding(self):
        class _Response:
            def json(self):
                return {"code": 403, "msg": "E_DEVICE_DISPOSED: device is disposed"}

        self.assertIn("E_DEVICE_DISPOSED", _business_result_error(_Response()))

    def test_motion_event_tts_hint_maps_terminal_and_accepted_phases(self):
        cases = {
            "accepted": "task_started",
            "done": "task_done",
            "failed": "task_failed",
            "rejected": "task_rejected",
        }

        for phase, tts_event in cases.items():
            with self.subTest(phase=phase):
                hint = motion_event_tts_hint(
                    {
                        "task_id": "task-1",
                        "device_id": "dev-1",
                        "capability": "home",
                        "phase": phase,
                        "source": "voice",
                    }
                )
                self.assertEqual(hint["tts_event"], tts_event)
                self.assertEqual(hint["task_id"], "task-1")
                self.assertEqual(hint["device_id"], "dev-1")
                self.assertEqual(hint["capability"], "home")
                self.assertEqual(hint["source"], "voice")
                self.assertTrue(hint["text"])

    def test_motion_event_tts_hint_ignores_running_progress_and_unknown_source(self):
        self.assertIsNone(motion_event_tts_hint({"phase": "running", "source": "voice"}))
        self.assertIsNone(motion_event_tts_hint({"phase": "progress", "source": "voice"}))
        self.assertIsNone(motion_event_tts_hint({"phase": "done"}))
        self.assertIsNone(motion_event_tts_hint({"phase": "done", "source": "system"}))

    def test_motion_event_queues_tts_without_business_forward_config(self):
        tts = FakeTTS()
        conn = SimpleNamespace(config={"server": {}}, tts=tts, sentence_id="sentence-1")
        msg = {
            "type": "motion_event",
            "session_id": "session-1",
            "task_id": "task-1",
            "device_id": "dev-1",
            "capability": "home",
            "phase": "accepted",
            "source": "client",
        }

        asyncio.run(MotionEventTextMessageHandler().handle(conn, msg))

        self.assertEqual(len(tts.sentences), 1)
        self.assertEqual(tts.stored[0]["sentence_id"], "sentence-1")

    def test_motion_event_forwarding_includes_tts_hint(self):
        import core.handle.textHandler.motionEventMessageHandler as module

        original = module.httpx.AsyncClient
        module.httpx.AsyncClient = FakeAsyncClient
        FakeAsyncClient.calls = []
        try:
            conn = SimpleNamespace(
                config={
                    "server": {
                        "motion_event_business_base_url": "http://business.local/",
                        "internal_motion_task_token": "secret-token",
                    }
                },
                tts=FakeTTS(),
                sentence_id="sentence-1",
            )
            msg = {
                "type": "motion_event",
                "session_id": "session-1",
                "task_id": "task-1",
                "device_id": "dev-1",
                "capability": "home",
                "phase": "done",
                "source": "voice",
            }

            asyncio.run(MotionEventTextMessageHandler().handle(conn, msg))
        finally:
            module.httpx.AsyncClient = original

        self.assertEqual(len(FakeAsyncClient.calls), 1)
        call = FakeAsyncClient.calls[0]
        self.assertEqual(call["url"], "http://business.local/internal/v1/motion_event")
        self.assertEqual(call["headers"], {"Authorization": "Bearer secret-token"})
        self.assertNotIn("type", call["json"])
        self.assertNotIn("session_id", call["json"])
        self.assertEqual(call["json"]["tts_hint"]["tts_event"], "task_done")

    def test_motion_event_forwarding_keeps_progress_without_tts_hint(self):
        import core.handle.textHandler.motionEventMessageHandler as module

        original = module.httpx.AsyncClient
        module.httpx.AsyncClient = FakeAsyncClient
        FakeAsyncClient.calls = []
        try:
            conn = SimpleNamespace(
                config={
                    "server": {
                        "motion_event_business_base_url": "http://business.local/",
                        "internal_motion_task_token": "secret-token",
                    }
                },
                tts=FakeTTS(),
                sentence_id="sentence-1",
            )
            msg = {
                "type": "motion_event",
                "session_id": "session-1",
                "task_id": "task-path",
                "device_id": "dev-1",
                "capability": "run_path",
                "phase": "progress",
                "source": "voice",
                "progress": {"done_segments": 1, "total_segments": 4, "percent": 25},
            }

            asyncio.run(MotionEventTextMessageHandler().handle(conn, msg))
        finally:
            module.httpx.AsyncClient = original

        self.assertEqual(len(FakeAsyncClient.calls), 1)
        call = FakeAsyncClient.calls[0]
        self.assertEqual(call["json"]["phase"], "progress")
        self.assertEqual(call["json"]["progress"]["percent"], 25)
        self.assertNotIn("tts_hint", call["json"])


if __name__ == "__main__":
    unittest.main()
