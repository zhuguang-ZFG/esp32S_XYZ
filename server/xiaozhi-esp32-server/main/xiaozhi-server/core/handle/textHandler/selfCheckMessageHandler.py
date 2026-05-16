"""M5.7 startup self_check uplink handling."""

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


def _business_result_error(response) -> str:
    try:
        payload = response.json()
    except (ValueError, AttributeError):
        return ""
    if not isinstance(payload, dict) or "code" not in payload:
        return ""
    if str(payload.get("code")) == "0":
        return ""
    return str(payload.get("msg") or payload.get("message") or "business result error")


class SelfCheckTextMessageHandler(TextMessageHandler):
    """Forward type=self_check startup diagnostic events to manager-api."""

    def __init__(self):
        self.logger = setup_logging()

    @property
    def message_type(self) -> TextMessageType:
        return TextMessageType.SELF_CHECK

    async def handle(self, conn: "ConnectionHandler", msg_json: Dict[str, Any]) -> None:
        server_cfg = conn.config.get("server") or {}
        if not isinstance(server_cfg, dict):
            return
        payload = _business_payload(msg_json)
        base = (
            server_cfg.get("self_check_business_base_url")
            or server_cfg.get("motion_event_business_base_url")
            or ""
        ).strip().rstrip("/")
        token = (server_cfg.get("internal_motion_task_token") or "").strip()
        if not base or not token:
            self.logger.bind(tag=TAG).debug("self_check forwarding skipped: business base url or token not configured")
            return

        url = f"{base}/internal/v1/self_check"
        headers = {"Authorization": f"Bearer {token}"}
        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                resp = await client.post(url, json=payload, headers=headers)
            if resp.status_code >= 400:
                self.logger.bind(tag=TAG).warning(
                    "self_check forwarding failed HTTP {} body={}",
                    resp.status_code,
                    resp.text[:500],
                )
            elif error := _business_result_error(resp):
                self.logger.bind(tag=TAG).warning(
                    "self_check forwarding rejected by business server: {}",
                    error,
                )
            else:
                self.logger.bind(tag=TAG).info(
                    "self_check forwarded device_id={} status={}",
                    payload.get("device_id"),
                    payload.get("status"),
                )
        except httpx.HTTPError as e:
            self.logger.bind(tag=TAG).warning("self_check forwarding HTTP error: {}", e)
        except OSError as e:
            self.logger.bind(tag=TAG).warning("self_check forwarding IO error: {}", e)
