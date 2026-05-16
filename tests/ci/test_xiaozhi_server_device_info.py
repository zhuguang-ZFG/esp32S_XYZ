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

from core.handle.textHandler.deviceInfoMessageHandler import (  # noqa: E402
    DeviceInfoTextMessageHandler,
    _business_payload,
    _business_result_error,
    device_info_tts_hint,
    device_info_tts_summary,
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
        self.sentences.append(
            {
                "content_type": content_type,
                "content_detail": content_detail,
                "sentence_id": sentence_id,
            }
        )

    def store_tts_text(self, sentence_id, text):
        self.stored.append({"sentence_id": sentence_id, "text": text})


class DeviceInfoHandlerTests(unittest.TestCase):
    def test_business_payload_strips_transport_fields(self):
        payload = _business_payload(
            {
                "type": "device_info",
                "session_id": "session-1",
                "task_id": "task-info",
                "device_id": "dev-1",
                "model": "DLC Motor Control P1 XYYZ",
            }
        )

        self.assertEqual(
            payload,
            {
                "task_id": "task-info",
                "device_id": "dev-1",
                "model": "DLC Motor Control P1 XYYZ",
            },
        )

    def test_business_result_error_detects_rejected_device_info_forwarding(self):
        class _Response:
            def json(self):
                return {"code": 403, "msg": "E_DEVICE_DISPOSED: device is disposed"}

        self.assertIn("E_DEVICE_DISPOSED", _business_result_error(_Response()))

    def test_registry_supports_device_info_message_type(self):
        registry_source = (
            SERVER_ROOT / "core" / "handle" / "textMessageHandlerRegistry.py"
        ).read_text(encoding="utf-8")
        type_source = (
            SERVER_ROOT / "core" / "handle" / "textMessageType.py"
        ).read_text(encoding="utf-8")

        self.assertIn('DEVICE_INFO = "device_info"', type_source)
        self.assertIn("DeviceInfoTextMessageHandler()", registry_source)

    def test_device_info_tts_summary_includes_identity_and_workspace(self):
        text = device_info_tts_summary(
            {
                "model": "DLC Motor Control P1 XYYZ",
                "hw_rev": "DLC_Motor_Control_P1_V1.0_260513",
                "fw_rev": "fake-u1",
                "workspace_mm": {"x": 200.0, "y": 150.5, "z": 50},
            }
        )

        self.assertIn("DLC Motor Control P1 XYYZ", text)
        self.assertIn("DLC_Motor_Control_P1_V1.0_260513", text)
        self.assertIn("fake-u1", text)
        self.assertIn("X 200", text)
        self.assertIn("Y 150.5", text)
        self.assertIn("Z 50", text)

    def test_device_info_tts_hint_has_event_and_identity(self):
        hint = device_info_tts_hint(
            {
                "task_id": "task-info",
                "device_id": "dev-1",
                "model": "DLC Motor Control P1 XYYZ",
                "workspace_mm": {"x": 200.0, "y": 150.0, "z": 50.0},
            }
        )

        self.assertEqual(hint["tts_event"], "device_info_reply")
        self.assertEqual(hint["task_id"], "task-info")
        self.assertEqual(hint["device_id"], "dev-1")
        self.assertIn("DLC Motor Control P1 XYYZ", hint["text"])

    def test_device_info_reply_queues_tts_without_business_forward_config(self):
        tts = FakeTTS()
        conn = SimpleNamespace(config={"server": {}}, tts=tts, sentence_id="sentence-1")
        msg = {
            "type": "device_info",
            "session_id": "session-1",
            "task_id": "task-info",
            "device_id": "dev-1",
            "model": "DLC Motor Control P1 XYYZ",
            "hw_rev": "DLC_Motor_Control_P1_V1.0_260513",
            "fw_rev": "fake-u1",
            "workspace_mm": {"x": 200.0, "y": 150.0, "z": 50.0},
        }

        asyncio.run(DeviceInfoTextMessageHandler().handle(conn, msg))

        self.assertEqual(len(tts.sentences), 1)
        self.assertIn("DLC Motor Control P1 XYYZ", tts.sentences[0]["content_detail"])
        self.assertEqual(tts.stored[0]["sentence_id"], "sentence-1")

    def test_forwards_device_info_to_business_server(self):
        import core.handle.textHandler.deviceInfoMessageHandler as module

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
                }
            )
            msg = {
                "type": "device_info",
                "session_id": "session-1",
                "task_id": "task-info",
                "device_id": "dev-1",
                "model": "DLC Motor Control P1 XYYZ",
                "hw_rev": "DLC_Motor_Control_P1_V1.0_260513",
                "fw_rev": "fake-u1",
                "workspace_mm": {"x": 200.0, "y": 150.0, "z": 50.0},
            }

            asyncio.run(DeviceInfoTextMessageHandler().handle(conn, msg))
        finally:
            module.httpx.AsyncClient = original

        self.assertEqual(len(FakeAsyncClient.calls), 1)
        call = FakeAsyncClient.calls[0]
        self.assertEqual(call["url"], "http://business.local/internal/v1/device_info")
        self.assertEqual(call["headers"], {"Authorization": "Bearer secret-token"})
        self.assertNotIn("type", call["json"])
        self.assertNotIn("session_id", call["json"])
        self.assertEqual(call["json"]["workspace_mm"], {"x": 200.0, "y": 150.0, "z": 50.0})
        self.assertEqual(call["json"]["tts_hint"]["tts_event"], "device_info_reply")


if __name__ == "__main__":
    unittest.main()
