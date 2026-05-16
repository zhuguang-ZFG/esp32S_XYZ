"""M2.6：设备经 WSS 上行的 motion_event，可选 POST 至 BusinessServer（manager-api）。"""

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


_PHASE_TO_TTS_EVENT = {
    "accepted": "task_started",
    "done": "task_done",
    "failed": "task_failed",
    "cancelled": "task_failed",
    "rejected": "task_rejected",
}

_TTS_EVENT_TEXT = {
    "task_started": "\u4efb\u52a1\u5df2\u5f00\u59cb\u3002",
    "task_done": "\u4efb\u52a1\u5df2\u5b8c\u6210\u3002",
    "task_failed": "\u4efb\u52a1\u6267\u884c\u5931\u8d25\u3002",
    "task_rejected": "\u4efb\u52a1\u5df2\u62d2\u7edd\u3002",
}


def _normalized_str(value: Any) -> str:
    return str(value or "").strip()


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


def motion_event_tts_hint(payload: Dict[str, Any]) -> Dict[str, Any] | None:
    source = _normalized_str(payload.get("source")).lower()
    if source not in ("voice", "client"):
        return None

    phase = _normalized_str(payload.get("phase")).lower()
    tts_event = _PHASE_TO_TTS_EVENT.get(phase)
    if not tts_event:
        return None

    hint: Dict[str, Any] = {
        "tts_event": tts_event,
        "text": _TTS_EVENT_TEXT[tts_event],
        "source": source,
    }
    for key in ("task_id", "device_id", "capability"):
        value = _normalized_str(payload.get(key))
        if value:
            hint[key] = value
    return hint


def _enqueue_tts_hint(conn: "ConnectionHandler", hint: Dict[str, Any], logger) -> None:
    tts = getattr(conn, "tts", None)
    if tts is None:
        logger.bind(tag=TAG).debug("motion_event TTS 宸茶烦杩囷紙褰撳墠杩炴帴 TTS 灏氭湭鍒濆鍖栵級")
        return
    text = str(hint.get("text") or "").strip()
    if not text:
        return
    try:
        tts.tts_one_sentence(conn, ContentType.TEXT, content_detail=text)
        if hasattr(tts, "store_tts_text"):
            tts.store_tts_text(getattr(conn, "sentence_id", None), text)
        logger.bind(tag=TAG).info(
            "motion_event TTS 宸插叆闃?task_id={} tts_event={}",
            hint.get("task_id"),
            hint.get("tts_event"),
        )
    except Exception as e:
        logger.bind(tag=TAG).warning("motion_event TTS 鍏ラ槦澶辫触: {}", e)


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
        payload = _business_payload(msg_json)
        hint = motion_event_tts_hint(payload)
        if hint:
            payload["tts_hint"] = hint
            _enqueue_tts_hint(conn, hint, self.logger)

        base = (server_cfg.get("motion_event_business_base_url") or "").strip().rstrip("/")
        token = (server_cfg.get("internal_motion_task_token") or "").strip()
        if not base or not token:
            self.logger.bind(tag=TAG).debug(
                "motion_event 已忽略（未配置 server.motion_event_business_base_url 或 server.internal_motion_task_token）"
            )
            return

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
            elif error := _business_result_error(resp):
                self.logger.bind(tag=TAG).warning(
                    "motion_event forwarding rejected by business server: {}",
                    error,
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
