import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server" / "xiaozhi-esp32-server" / "main" / "xiaozhi-server"

WEBSOCKET_SERVER = SERVER / "core" / "websocket_server.py"
MANAGE_API_CLIENT = SERVER / "config" / "manage_api_client.py"


class XiaozhiServerDisposedAuthTests(unittest.TestCase):
    def test_websocket_auth_checks_disposed_status_before_local_auth_bypass(self):
        text = WEBSOCKET_SERVER.read_text(encoding="utf-8", errors="replace")

        handle_auth = text.index("async def _handle_auth")
        disposed_check = text.index("await self._reject_disposed_device_if_needed(device_id)")
        allowed_devices = text.index("self.allowed_devices and device_id in self.allowed_devices")

        self.assertLess(handle_auth, disposed_check)
        self.assertLess(disposed_check, allowed_devices)
        self.assertIn("get_device_runtime_status(device_id)", text)
        self.assertIn('raise AuthenticationError("E_DEVICE_DISPOSED")', text)
        self.assertIn('raise AuthenticationError("E_RUNTIME_STATUS_UNAVAILABLE")', text)
        self.assertIn("ManageApiClient._instance is not None", text)
        self.assertLess(text.index("device runtime status check failed"), text.index('raise AuthenticationError("E_RUNTIME_STATUS_UNAVAILABLE")'))

    def test_manage_api_client_exposes_device_runtime_status_wrapper(self):
        text = MANAGE_API_CLIENT.read_text(encoding="utf-8", errors="replace")

        self.assertIn("async def get_device_runtime_status", text)
        self.assertIn('"/config/device-runtime-status"', text)
        self.assertIn('json={"deviceId": device_id}', text)


if __name__ == "__main__":
    unittest.main()
