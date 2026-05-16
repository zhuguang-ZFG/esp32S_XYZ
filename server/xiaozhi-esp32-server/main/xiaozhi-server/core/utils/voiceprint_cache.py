"""Minimal v2 voiceprint cache and policy helpers."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
import time
from typing import Iterable, Optional

import httpx


@dataclass(frozen=True)
class VoiceprintEntry:
    member_id: int
    display_name: str
    member_type: str
    speaker_ref: str
    embedding_hash: str
    status: str = "active"
    expires_at: Optional[datetime] = None

    @property
    def reenroll_required(self) -> bool:
        return self.expires_at is not None and self.expires_at <= _now_utc()


class ActiveVoiceprintCache:
    def __init__(self):
        self._by_device: dict[str, list[VoiceprintEntry]] = {}
        self._loaded_at: dict[str, float] = {}

    def update_device(self, device_id: str, entries: Iterable[dict]) -> list[VoiceprintEntry]:
        normalized_device_id = _required_text(device_id)
        parsed = [_entry_from_payload(item) for item in entries]
        active = [entry for entry in parsed if entry.status == "active"]
        self._by_device[normalized_device_id] = active
        self._loaded_at[normalized_device_id] = time.monotonic()
        return list(active)

    def clear_device(self, device_id: str) -> int:
        normalized_device_id = _required_text(device_id)
        removed = len(self._by_device.pop(normalized_device_id, []))
        self._loaded_at.pop(normalized_device_id, None)
        return removed

    def entries_for_device(self, device_id: str) -> list[VoiceprintEntry]:
        return list(self._by_device.get(_required_text(device_id), []))

    def is_fresh(self, device_id: str, ttl_seconds: int) -> bool:
        normalized_device_id = _required_text(device_id)
        loaded_at = self._loaded_at.get(normalized_device_id)
        if loaded_at is None:
            return False
        return time.monotonic() - loaded_at <= ttl_seconds


def voiceprint_mode_from_config(config: dict) -> str:
    voiceprint_cfg = config.get("voiceprint") if isinstance(config, dict) else {}
    if not isinstance(voiceprint_cfg, dict):
        voiceprint_cfg = {}
    server_cfg = config.get("server") if isinstance(config, dict) else {}
    if not isinstance(server_cfg, dict):
        server_cfg = {}
    mode = voiceprint_cfg.get("mode") or server_cfg.get("voiceprint_mode") or "voiceprint_off"
    return str(mode or "voiceprint_off").strip().lower()


async def refresh_voiceprint_cache_from_business(
    config: dict,
    device_id: str,
    cache: ActiveVoiceprintCache,
    logger=None,
) -> list[VoiceprintEntry]:
    normalized_device_id = _required_text(device_id)
    server_cfg = config.get("server") if isinstance(config, dict) else {}
    if not isinstance(server_cfg, dict):
        server_cfg = {}
    base = (
        server_cfg.get("voiceprint_cache_business_base_url")
        or server_cfg.get("voice_task_business_base_url")
        or server_cfg.get("motion_event_business_base_url")
        or ""
    )
    base = str(base or "").strip().rstrip("/")
    token = str(server_cfg.get("internal_motion_task_token") or "").strip()
    if not base or not token:
        _log(logger, "debug", "voiceprint cache refresh skipped; base/token missing")
        return cache.entries_for_device(normalized_device_id)

    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            response = await client.post(
                f"{base}/internal/v1/voiceprints/cache",
                json={"device_id": normalized_device_id},
                headers={"Authorization": f"Bearer {token}"},
            )
        if response.status_code >= 400:
            _log(
                logger,
                "warning",
                "voiceprint cache refresh failed HTTP {}",
                response.status_code,
            )
            return cache.entries_for_device(normalized_device_id)
        payload = response.json()
        if isinstance(payload, dict) and _is_result_error(payload):
            message = str(payload.get("msg") or payload.get("message") or "")
            _log(logger, "warning", "voiceprint cache refresh business error: {}", message)
            if _should_clear_cache_on_business_error(message):
                return cache.update_device(normalized_device_id, [])
            return cache.entries_for_device(normalized_device_id)
        entries = payload.get("data") if isinstance(payload, dict) else payload
        if not isinstance(entries, list):
            _log(logger, "warning", "voiceprint cache refresh returned non-list data")
            return cache.entries_for_device(normalized_device_id)
        return cache.update_device(normalized_device_id, entries)
    except (httpx.HTTPError, OSError, ValueError) as exc:
        _log(logger, "warning", "voiceprint cache refresh error: {}", exc)
        return cache.entries_for_device(normalized_device_id)


def _log(logger, level: str, message: str, *args) -> None:
    if logger is None:
        return
    try:
        getattr(logger, level)(message, *args)
    except AttributeError:
        pass


def _is_result_error(payload: dict) -> bool:
    if "code" not in payload:
        return False
    return str(payload.get("code")) != "0"


def _should_clear_cache_on_business_error(message: str) -> bool:
    return "E_DEVICE_DISPOSED" in message or "DEVICE_NOT_EXIST" in message


def decide_voiceprint_policy(
    mode: str,
    entries: Iterable[VoiceprintEntry],
    speaker_ref: Optional[str],
) -> dict[str, object]:
    normalized_mode = (mode or "voiceprint_off").strip().lower()
    active_entries = [entry for entry in entries if entry.status == "active"]
    if normalized_mode == "voiceprint_off":
        return {"allowed": True, "reason": "voiceprint_off", "member": None}

    matched = next(
        (entry for entry in active_entries if speaker_ref and entry.speaker_ref == speaker_ref),
        None,
    )
    if matched is not None:
        reason = "child_reenroll_required" if matched.member_type == "child" and matched.reenroll_required else "matched"
        return {"allowed": True, "reason": reason, "member": matched}

    if normalized_mode == "loose":
        return {"allowed": True, "reason": "unknown_allowed", "member": None}
    if normalized_mode == "child":
        child_entries = [entry for entry in active_entries if entry.member_type == "child"]
        reason = "child_unknown_allowed" if child_entries else "child_no_profile_allowed"
        return {"allowed": True, "reason": reason, "member": None}
    if normalized_mode == "strict":
        return {"allowed": False, "reason": "unknown_rejected", "member": None}
    return {"allowed": False, "reason": "invalid_mode", "member": None}


def _entry_from_payload(payload: dict) -> VoiceprintEntry:
    return VoiceprintEntry(
        member_id=int(payload.get("memberId") or payload.get("member_id") or 0),
        display_name=str(payload.get("displayName") or payload.get("display_name") or "").strip(),
        member_type=str(payload.get("memberType") or payload.get("member_type") or "").strip().lower(),
        speaker_ref=_required_text(payload.get("speakerRef") or payload.get("speaker_ref")),
        embedding_hash=_required_text(payload.get("embeddingHash") or payload.get("embedding_hash")),
        status=str(payload.get("status") or "active").strip().lower(),
        expires_at=_parse_datetime(payload.get("expiresAt") or payload.get("expires_at")),
    )


def _required_text(value: object) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError("value is required")
    return text


def _parse_datetime(value: object) -> Optional[datetime]:
    if value is None:
        return None
    if isinstance(value, datetime):
        parsed = value
    else:
        text = str(value or "").strip()
        if not text:
            return None
        if text.endswith("Z"):
            text = text[:-1] + "+00:00"
        try:
            parsed = datetime.fromisoformat(text)
        except ValueError:
            return None
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def _now_utc() -> datetime:
    return datetime.now(timezone.utc)
