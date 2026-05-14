"""M2.6：设备经 WSS 上行的 motion_event，可选 POST 至 BusinessServer（manager-api）。"""

from typing import Any, Dict, TYPE_CHECKING

import httpx

from config.logger import setup_logging
from core.handle.textMessageHandler import TextMessageHandler
from core.handle.textMessageType import TextMessageType

TAG = __name__

if TYPE_CHECKING:
    from core.connection import ConnectionHandler


def _business_payload(msg: Dict[str, Any]) -> Dict[str, Any]:
    return {k: v for k, v in msg.items() if k not in ("type", "session_id")}


class MotionEventTextMessageHandler(TextMessageHandler):
    """与固件 `Protocol::SendMotionEvent` 对齐：type=motion_event，其余字段原样转发（去掉会话字段）。"""

    def __init__(self):
        self.logger = setup_logging()

    @property
    def message_type(self) -> TextMessageType:
        return TextMessageType.MOTION_EVENT

    async def handle(self, conn: "ConnectionHandler", msg_json: Dict[str, Any]) -> None:
        server_cfg = conn.config.get("server") or {}
        if not isinstance(server_cfg, dict):
            return
        base = (server_cfg.get("motion_event_business_base_url") or "").strip().rstrip("/")
        token = (server_cfg.get("internal_motion_task_token") or "").strip()
        if not base or not token:
            self.logger.bind(tag=TAG).debug(
                "motion_event 已忽略（未配置 server.motion_event_business_base_url 或 server.internal_motion_task_token）"
            )
            return

        payload = _business_payload(msg_json)
        url = f"{base}/internal/v1/motion_event"
        headers = {"Authorization": f"Bearer {token}"}
        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                resp = await client.post(url, json=payload, headers=headers)
            if resp.status_code >= 400:
                self.logger.bind(tag=TAG).warning(
                    "motion_event 转发失败 HTTP {} body={}",
                    resp.status_code,
                    resp.text[:500],
                )
            else:
                self.logger.bind(tag=TAG).info(
                    "motion_event 已转发 task_id={} phase={}",
                    payload.get("task_id"),
                    payload.get("phase"),
                )
        except httpx.HTTPError as e:
            self.logger.bind(tag=TAG).warning("motion_event 转发 HTTP 异常: {}", e)
        except OSError as e:
            self.logger.bind(tag=TAG).warning("motion_event 转发 IO 异常: {}", e)
