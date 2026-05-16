"""M2.13/M2.15 device_info uplink handling."""

import json
from typing import Any, Dict, TYPE_CHECKING

import httpx

from config.logger import setup_logging
from core.handle.textMessageHandler import TextMessageHandler
from core.handle.textMessageType import TextMessageType
from core.providers.tts.dto.dto import ContentType

TAG = __name__

if TYPE_CHECKING:
    from core.connection import ConnectionHandler


def _business_payload(msg: Dict[str, Any]) -> Dict[str, Any]:
    return {k: v for k, v in msg.items() if k not in ("type", "session_id")}


def _format_mm(value: Any) -> str:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return "\u672a\u77e5"
    if number.is_integer():
        return str(int(number))
    return f"{number:.1f}".rstrip("0").rstrip(".")


def _workspace_summary(value: Any) -> str:
    if isinstance(value, str):
        try:
            value = json.loads(value)
        except json.JSONDecodeError:
            return ""
    if not isinstance(value, dict):
        return ""
    x = _format_mm(value.get("x"))
    y = _format_mm(value.get("y"))
    z = _format_mm(value.get("z"))
    if "\u672a\u77e5" in (x, y, z):
        return ""
    return f"\u5de5\u4f5c\u7a7a\u95f4 X {x}\uff0cY {y}\uff0cZ {z} \u6beb\u7c73"


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


def device_info_tts_summary(payload: Dict[str, Any]) -> str:
    model = str(payload.get("model") or "\u672a\u77e5\u578b\u53f7").strip()
    hw_rev = str(payload.get("hw_rev") or "").strip()
    fw_rev = str(payload.get("fw_rev") or "").strip()
    parts = [f"\u8fd9\u53f0\u673a\u5668\u578b\u53f7\u662f {model}"]
    if hw_rev:
        parts.append(f"\u786c\u4ef6\u7248\u672c {hw_rev}")
    if fw_rev:
        parts.append(f"\u56fa\u4ef6\u7248\u672c {fw_rev}")
    workspace = _workspace_summary(payload.get("workspace_mm"))
    if workspace:
        parts.append(workspace)
    return "\uff0c".join(parts) + "\u3002"


def device_info_tts_hint(payload: Dict[str, Any]) -> Dict[str, Any]:
    hint: Dict[str, Any] = {
        "tts_event": "device_info_reply",
        "text": device_info_tts_summary(payload),
    }
    for key in ("task_id", "device_id"):
        value = str(payload.get(key) or "").strip()
        if value:
            hint[key] = value
    return hint


def _enqueue_device_info_tts(conn: "ConnectionHandler", hint: Dict[str, Any], logger) -> None:
    tts = getattr(conn, "tts", None)
    if tts is None:
        logger.bind(tag=TAG).debug("device_info TTS skipped: no TTS provider on connection")
        return
    text = str(hint.get("text") or "").strip()
    if not text:
        return
    try:
        tts.tts_one_sentence(conn, ContentType.TEXT, content_detail=text)
        if hasattr(tts, "store_tts_text"):
            tts.store_tts_text(getattr(conn, "sentence_id", None), text)
        logger.bind(tag=TAG).info(
            "device_info_reply TTS queued task_id={} text={}",
            hint.get("task_id"),
            text,
        )
    except Exception as e:
        logger.bind(tag=TAG).warning("device_info_reply TTS queue failed: {}", e)


class DeviceInfoTextMessageHandler(TextMessageHandler):
    """Forward type=device_info snapshots and enqueue the device info TTS hint."""

    def __init__(self):
        self.logger = setup_logging()

    @property
    def message_type(self) -> TextMessageType:
        return TextMessageType.DEVICE_INFO

    async def handle(self, conn: "ConnectionHandler", msg_json: Dict[str, Any]) -> None:
        server_cfg = conn.config.get("server") or {}
        if not isinstance(server_cfg, dict):
            return
        payload = _business_payload(msg_json)
        hint = device_info_tts_hint(payload)
        payload["tts_hint"] = hint
        _enqueue_device_info_tts(conn, hint, self.logger)
        base = (
            server_cfg.get("device_info_business_base_url")
            or server_cfg.get("motion_event_business_base_url")
            or ""
        ).strip().rstrip("/")
        token = (server_cfg.get("internal_motion_task_token") or "").strip()
        if not base or not token:
            self.logger.bind(tag=TAG).debug("device_info forwarding skipped: business base url or token not configured")
            return

        url = f"{base}/internal/v1/device_info"
        headers = {"Authorization": f"Bearer {token}"}
        try:
            async with httpx.AsyncClient(timeout=15.0) as client:
                resp = await client.post(url, json=payload, headers=headers)
            if resp.status_code >= 400:
                self.logger.bind(tag=TAG).warning(
                    "device_info forwarding failed HTTP {} body={}",
                    resp.status_code,
                    resp.text[:500],
                )
            elif error := _business_result_error(resp):
                self.logger.bind(tag=TAG).warning(
                    "device_info forwarding rejected by business server: {}",
                    error,
                )
            else:
                self.logger.bind(tag=TAG).info(
                    "device_info forwarded task_id={} device_id={} model={}",
                    payload.get("task_id"),
                    payload.get("device_id"),
                    payload.get("model"),
                )
        except httpx.HTTPError as e:
            self.logger.bind(tag=TAG).warning("device_info forwarding HTTP error: {}", e)
        except OSError as e:
            self.logger.bind(tag=TAG).warning("device_info forwarding IO error: {}", e)
