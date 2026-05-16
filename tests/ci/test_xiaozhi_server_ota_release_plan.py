import asyncio
import sys
import unittest
from pathlib import Path
from types import ModuleType


SERVER_ROOT = Path(__file__).resolve().parents[2] / "server" / "xiaozhi-esp32-server" / "main" / "xiaozhi-server"
sys.path.insert(0, str(SERVER_ROOT))


class FakeLogger:
    def bind(self, **kwargs):
        return self

    def info(self, *args, **kwargs):
        pass

    def warning(self, *args, **kwargs):
        pass

    def error(self, *args, **kwargs):
        pass


logger_module = ModuleType("config.logger")
logger_module.setup_logging = lambda: FakeLogger()
sys.modules.setdefault("config.logger", logger_module)

aiohttp_module = ModuleType("aiohttp")
web_module = ModuleType("aiohttp.web")
web_module.Response = object
web_module.FileResponse = object
web_module.HTTPBadRequest = Exception
web_module.HTTPError = Exception
web_module.HTTPForbidden = Exception
web_module.HTTPNotFound = Exception
aiohttp_module.web = web_module
sys.modules.setdefault("aiohttp", aiohttp_module)
sys.modules.setdefault("aiohttp.web", web_module)

util_module = ModuleType("core.utils.util")
util_module.get_local_ip = lambda: "127.0.0.1"
util_module.get_vision_url = lambda config: "http://127.0.0.1:8003/mcp/vision/explain"
sys.modules.setdefault("core.utils.util", util_module)

from core.api.ota_handler import (  # noqa: E402
    OTAHandler,
    _extract_device_model,
    _extract_device_version,
    _extract_firmware_channel,
    _select_local_firmware,
)


class FakeResponse:
    status_code = 200
    text = "ok"

    def json(self):
        return {
            "code": 0,
            "data": {
                "releaseId": "rel-1",
                "version": "1.2.3",
                "url": "https://ota.example.com/u8.bin",
                "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "signature": "c2ln",
            },
        }


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


class OTAReleasePlanTests(unittest.TestCase):
    def test_extracts_device_metadata_with_header_precedence(self):
        headers = {
            "device-model": "header-model",
            "device-version": "2.0.0",
            "firmware-channel": "beta",
        }
        body = {
            "board": {"type": "body-model"},
            "application": {"version": "1.0.0"},
            "channel": "dev",
        }

        self.assertEqual(_extract_device_model(headers, body), "header-model")
        self.assertEqual(_extract_device_version(headers, body), "2.0.0")
        self.assertEqual(_extract_firmware_channel(headers, body), "beta")

    def test_extracts_device_metadata_body_fallbacks(self):
        body = {
            "board": {"type": "body-model"},
            "application": {"version": "1.0.0"},
            "channel": "dev",
        }

        self.assertEqual(_extract_device_model({}, body), "body-model")
        self.assertEqual(_extract_device_version({}, body), "1.0.0")
        self.assertEqual(_extract_firmware_channel({}, body), "dev")

    def test_extracts_device_metadata_defaults(self):
        self.assertEqual(_extract_device_model({}, {}), "default")
        self.assertEqual(_extract_device_version({}, {}), "0.0.0")
        self.assertEqual(_extract_firmware_channel({}, {}), "dev")

    def test_selects_first_higher_local_firmware(self):
        version, url = _select_local_firmware(
            [("1.2.0", "model_1.2.0.bin"), ("1.1.0", "model_1.1.0.bin")],
            "1.1.5",
            {},
        )

        self.assertEqual(version, "1.2.0")
        self.assertEqual(url, "http://127.0.0.1:8003/xiaozhi/ota/download/model_1.2.0.bin")

    def test_keeps_current_version_when_no_local_firmware_is_newer(self):
        version, url = _select_local_firmware(
            [("1.0.0", "model_1.0.0.bin")],
            "1.1.0",
            {},
        )

        self.assertEqual(version, "1.1.0")
        self.assertEqual(url, "")

    def test_fetches_signed_business_firmware_plan(self):
        import core.api.ota_handler as module

        original = module.httpx.AsyncClient
        module.httpx.AsyncClient = FakeAsyncClient
        FakeAsyncClient.calls = []
        try:
            handler = OTAHandler(
                {
                    "server": {
                        "auth_key": "secret",
                        "auth": {},
                        "firmware_release_business_base_url": "http://business.local/",
                        "internal_motion_task_token": "secret-token",
                    }
                }
            )
            plan = asyncio.run(handler._fetch_business_firmware_plan("dev-1", "dev", "1.0.0"))
        finally:
            module.httpx.AsyncClient = original

        self.assertEqual(plan["version"], "1.2.3")
        self.assertEqual(len(FakeAsyncClient.calls), 1)
        call = FakeAsyncClient.calls[0]
        self.assertEqual(call["url"], "http://business.local/internal/v1/firmware/upgrade-plan")
        self.assertEqual(call["headers"], {"Authorization": "Bearer secret-token"})
        self.assertEqual(call["json"]["device_id"], "dev-1")
        self.assertEqual(call["json"]["current_version"], "1.0.0")

    def test_rejects_business_result_error_for_firmware_plan(self):
        import core.api.ota_handler as module

        class _ErrorResponse:
            status_code = 200
            text = "business rejected"

            def json(self):
                return {"code": 403, "msg": "E_DEVICE_DISPOSED: device is disposed", "data": {"version": "9.9.9"}}

        class _ErrorClient(FakeAsyncClient):
            async def post(self, url, json, headers):
                self.calls.append({"url": url, "json": json, "headers": headers, "timeout": self.timeout})
                return _ErrorResponse()

        original = module.httpx.AsyncClient
        module.httpx.AsyncClient = _ErrorClient
        _ErrorClient.calls = []
        try:
            handler = OTAHandler(
                {
                    "server": {
                        "auth_key": "secret",
                        "auth": {},
                        "firmware_release_business_base_url": "http://business.local/",
                        "internal_motion_task_token": "secret-token",
                    }
                }
            )
            plan = asyncio.run(handler._fetch_business_firmware_plan("dev-1", "dev", "1.0.0"))
        finally:
            module.httpx.AsyncClient = original

        self.assertIsNone(plan)

    def test_applies_signed_plan_to_ota_firmware_payload(self):
        handler = OTAHandler({"server": {"auth_key": "secret", "auth": {}}})
        payload = {"firmware": {"version": "1.0.0", "url": ""}}

        applied = handler._apply_business_firmware_plan(
            payload,
            {
                "releaseId": "rel-1",
                "version": "1.2.3",
                "url": "https://ota.example.com/u8.bin",
                "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "signature": "c2ln",
            },
        )

        self.assertTrue(applied)
        self.assertEqual(payload["firmware"]["version"], "1.2.3")
        self.assertEqual(payload["firmware"]["release_id"], "rel-1")
        self.assertEqual(payload["firmware"]["sha256"], "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
        self.assertEqual(payload["firmware"]["signature"], "c2ln")

    def test_forwards_firmware_install_result_to_business_server(self):
        import core.api.ota_handler as module

        original = module.httpx.AsyncClient
        module.httpx.AsyncClient = FakeAsyncClient
        FakeAsyncClient.calls = []
        try:
            handler = OTAHandler(
                {
                    "server": {
                        "auth_key": "secret",
                        "auth": {},
                        "firmware_release_business_base_url": "http://business.local/",
                        "internal_motion_task_token": "secret-token",
                    }
                }
            )
            forwarded = asyncio.run(
                handler._forward_business_install_result(
                    {
                        "device_id": "dev-1",
                        "release_id": "rel-1",
                        "success": False,
                    }
                )
            )
        finally:
            module.httpx.AsyncClient = original

        self.assertTrue(forwarded)
        call = FakeAsyncClient.calls[0]
        self.assertEqual(call["url"], "http://business.local/internal/v1/firmware/install-result")
        self.assertEqual(call["headers"], {"Authorization": "Bearer secret-token"})
        self.assertEqual(call["json"]["release_id"], "rel-1")
        self.assertFalse(call["json"]["success"])

    def test_install_result_rejects_business_result_error(self):
        import core.api.ota_handler as module

        class _ErrorResponse:
            status_code = 200
            text = "business rejected"

            def json(self):
                return {"code": 403, "msg": "E_DEVICE_DISPOSED: device is disposed"}

        class _ErrorClient(FakeAsyncClient):
            async def post(self, url, json, headers):
                self.calls.append({"url": url, "json": json, "headers": headers, "timeout": self.timeout})
                return _ErrorResponse()

        original = module.httpx.AsyncClient
        module.httpx.AsyncClient = _ErrorClient
        _ErrorClient.calls = []
        try:
            handler = OTAHandler(
                {
                    "server": {
                        "auth_key": "secret",
                        "auth": {},
                        "firmware_release_business_base_url": "http://business.local/",
                        "internal_motion_task_token": "secret-token",
                    }
                }
            )
            forwarded = asyncio.run(
                handler._forward_business_install_result(
                    {
                        "device_id": "dev-1",
                        "release_id": "rel-1",
                        "success": False,
                    }
                )
            )
        finally:
            module.httpx.AsyncClient = original

        self.assertFalse(forwarded)

    def test_rejects_incomplete_business_plan(self):
        handler = OTAHandler({"server": {"auth_key": "secret", "auth": {}}})
        payload = {"firmware": {"version": "1.0.0", "url": ""}}

        applied = handler._apply_business_firmware_plan(
            payload,
            {"version": "1.2.3", "url": "https://ota.example.com/u8.bin"},
        )

        self.assertFalse(applied)
        self.assertEqual(payload["firmware"], {"version": "1.0.0", "url": ""})

    def test_rejects_business_plan_metadata_the_u8_client_would_reject(self):
        handler = OTAHandler({"server": {"auth_key": "secret", "auth": {}}})
        valid_plan = {
            "releaseId": "rel-1",
            "version": "1.2.3",
            "url": "https://ota.example.com/u8.bin",
            "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "signature": "c2ln",
        }

        for invalid_field, invalid_value in (
            ("url", "http://ota.example.com/u8.bin"),
            ("sha256", "ABCDEF"),
            ("signature", "not base64!"),
        ):
            payload = {"firmware": {"version": "1.0.0", "url": ""}}
            plan = dict(valid_plan)
            plan[invalid_field] = invalid_value

            applied = handler._apply_business_firmware_plan(payload, plan)

            self.assertFalse(applied)
            self.assertEqual(payload["firmware"], {"version": "1.0.0", "url": ""})


if __name__ == "__main__":
    unittest.main()
