import json

from aiohttp import web

from config.logger import setup_logging
from core.handle.motionHandle import build_motion_task_websocket_message

TAG = __name__


class MotionTaskHandler:
    """BusinessServer → DeviceServer 内部 motion_task 入口（M2.3）；M2.4 转发至设备 WSS。"""

    def __init__(self, config: dict, websocket_server=None):
        self.config = config
        self.websocket_server = websocket_server
        self.logger = setup_logging()

    def _expected_token(self) -> str:
        server_cfg = self.config.get("server") or {}
        if not isinstance(server_cfg, dict):
            return ""
        raw = server_cfg.get("internal_motion_task_token", "")
        return (raw or "").strip()

    async def handle_post(self, request: web.Request) -> web.Response:
        token = self._expected_token()
        if not token:
            self.logger.bind(tag=TAG).warning(
                "收到 /internal/v1/motion_task 请求，但 server.internal_motion_task_token 未配置，拒绝处理"
            )
            return web.json_response(
                {"ok": False, "error": "internal_motion_task_token not configured"},
                status=503,
            )

        auth = request.headers.get("Authorization", "")
        if not auth.startswith("Bearer "):
            return web.json_response({"ok": False, "error": "missing bearer token"}, status=401)
        provided = auth[len("Bearer ") :].strip()
        if provided != token:
            return web.json_response({"ok": False, "error": "invalid token"}, status=401)

        try:
            body = await request.json()
        except json.JSONDecodeError:
            return web.json_response({"ok": False, "error": "invalid json"}, status=400)

        device_id = body.get("device_id")
        if not device_id or not isinstance(device_id, str):
            return web.json_response(
                {"ok": False, "error": "device_id required"},
                status=400,
            )

        self.logger.bind(tag=TAG).info(
            "motion_task accepted task_id={} device_id={} capability={}",
            body.get("task_id"),
            device_id,
            body.get("capability"),
        )

        if self.websocket_server is None:
            return web.json_response(
                {"ok": False, "error": "websocket_server not linked"},
                status=503,
            )

        wss_payload = build_motion_task_websocket_message(body)
        sent = await self.websocket_server.send_motion_task_to_device(
            device_id, wss_payload
        )
        if not sent:
            return web.json_response(
                {"ok": False, "error": "device_offline"},
                status=404,
            )

        self.logger.bind(tag=TAG).info(
            "motion_task 已派发到设备 WSS device_id={} task_id={}",
            device_id,
            body.get("task_id"),
        )
        return web.json_response({"ok": True, "received": True, "dispatched": True})

    async def handle_options(self, request: web.Request) -> web.Response:
        return web.Response(status=204)
