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

from core.handle.textHandler.selfCheckMessageHandler import (  # noqa: E402
    SelfCheckTextMessageHandler,
    _business_payload,
    _business_result_error,
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


class SelfCheckHandlerTests(unittest.TestCase):
    def test_business_payload_strips_transport_fields(self):
        payload = _business_payload(
            {
                "type": "self_check",
                "session_id": "session-1",
                "device_id": "dev-1",
                "status": "passed",
            }
        )

        self.assertEqual(payload, {"device_id": "dev-1", "status": "passed"})

    def test_business_result_error_detects_rejected_self_check_forwarding(self):
        class _Response:
            def json(self):
                return {"code": 403, "msg": "E_DEVICE_DISPOSED: device is disposed"}

        self.assertIn("E_DEVICE_DISPOSED", _business_result_error(_Response()))

    def test_registry_supports_self_check_message_type(self):
        registry_source = (
            SERVER_ROOT / "core" / "handle" / "textMessageHandlerRegistry.py"
        ).read_text(encoding="utf-8")
        type_source = (
            SERVER_ROOT / "core" / "handle" / "textMessageType.py"
        ).read_text(encoding="utf-8")

        self.assertIn('SELF_CHECK = "self_check"', type_source)
        self.assertIn("SelfCheckTextMessageHandler()", registry_source)

    def test_forwards_self_check_to_business_server(self):
        import core.handle.textHandler.selfCheckMessageHandler as module

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
                "type": "self_check",
                "session_id": "session-1",
                "device_id": "dev-1",
                "check_id": "startup",
                "scope": "startup",
                "status": "passed",
                "checks": {
                    "nvs": {"ok": True},
                    "wifi": {"ok": True},
                    "u1_uart": {"ok": True},
                    "audio": {"ok": True},
                },
            }

            asyncio.run(SelfCheckTextMessageHandler().handle(conn, msg))
        finally:
            module.httpx.AsyncClient = original

        self.assertEqual(len(FakeAsyncClient.calls), 1)
        call = FakeAsyncClient.calls[0]
        self.assertEqual(call["url"], "http://business.local/internal/v1/self_check")
        self.assertEqual(call["headers"], {"Authorization": "Bearer secret-token"})
        self.assertNotIn("type", call["json"])
        self.assertNotIn("session_id", call["json"])
        self.assertEqual(call["json"]["checks"]["u1_uart"], {"ok": True})


if __name__ == "__main__":
    unittest.main()
