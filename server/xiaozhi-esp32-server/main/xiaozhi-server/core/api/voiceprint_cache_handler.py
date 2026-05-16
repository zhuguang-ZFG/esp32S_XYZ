import json

from aiohttp import web

from config.logger import setup_logging

TAG = __name__


class VoiceprintCacheHandler:
    """Internal endpoint for BusinessServer to invalidate DeviceServer voiceprint cache."""

    def __init__(self, config: dict, websocket_server=None):
        self.config = config
        self.websocket_server = websocket_server
        self.logger = setup_logging()

    def _expected_token(self) -> str:
        server_cfg = self.config.get("server") or {}
        if not isinstance(server_cfg, dict):
            return ""
        return str(server_cfg.get("internal_motion_task_token") or "").strip()

    async def handle_clear(self, request: web.Request) -> web.Response:
        auth_error = self._authorize(request)
        if auth_error is not None:
            return auth_error
        try:
            body = await request.json()
        except json.JSONDecodeError:
            return web.json_response({"ok": False, "error": "invalid json"}, status=400)

        device_id = body.get("device_id")
        if not device_id or not isinstance(device_id, str):
            return web.json_response({"ok": False, "error": "device_id required"}, status=400)
        cache = getattr(self.websocket_server, "voiceprint_cache", None)
        if cache is None:
            return web.json_response({"ok": False, "error": "voiceprint_cache not linked"}, status=503)

        removed = cache.clear_device(device_id)
        self.logger.bind(tag=TAG).info(
            "voiceprint cache cleared device_id={} removed={} reason={}",
            device_id,
            removed,
            body.get("reason"),
        )
        return web.json_response({"ok": True, "device_id": device_id.strip(), "removed": removed})

    async def handle_options(self, request: web.Request) -> web.Response:
        return web.Response(status=204)

    def _authorize(self, request: web.Request):
        token = self._expected_token()
        if not token:
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
        return None
