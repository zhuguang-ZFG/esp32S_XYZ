"""Deterministic voice-to-device/task mapping for short commands."""

from __future__ import annotations

import re
from typing import Callable, Dict, Optional


ToolPredicate = Callable[[str], bool]

DIRECT_DEVICE_TOOL_RULES = (
    (
        "get_device_info",
        "self.motor.get_device_info",
        ("型号", "设备信息", "机器信息", "硬件版本", "固件版本", "device info", "what model"),
    ),
    (
        "home",
        "self.motor.home",
        ("归零", "回原点", "回零", "home"),
    ),
    (
        "pause",
        "self.motor.pause",
        ("暂停", "pause"),
    ),
    (
        "resume",
        "self.motor.resume",
        ("继续", "恢复", "resume"),
    ),
    (
        "stop",
        "self.motor.stop",
        ("停止", "停下", "stop"),
    ),
)

MOVE_REL_TOOL_NAME = "self.motor.move_rel"
DEFAULT_WRITE_TEXT_FONT_ID = "kai_basic_v1"
MOVE_REL_TOOL_RULES = (
    (
        ("\u5f80\u5de6\u4e00\u70b9", "\u5411\u5de6\u4e00\u70b9", "\u5de6\u4e00\u70b9", "left a bit"),
        {"dx": -1, "dy": 0, "dz": 0, "feed": 800},
    ),
    (
        ("\u5f80\u53f3\u4e00\u70b9", "\u5411\u53f3\u4e00\u70b9", "\u53f3\u4e00\u70b9", "right a bit"),
        {"dx": 1, "dy": 0, "dz": 0, "feed": 800},
    ),
    (
        ("\u5f80\u524d\u4e00\u70b9", "\u5411\u524d\u4e00\u70b9", "\u524d\u4e00\u70b9", "forward a bit"),
        {"dx": 0, "dy": 1, "dz": 0, "feed": 800},
    ),
    (
        ("\u5f80\u540e\u4e00\u70b9", "\u5411\u540e\u4e00\u70b9", "\u540e\u4e00\u70b9", "back a bit"),
        {"dx": 0, "dy": -1, "dz": 0, "feed": 800},
    ),
    (
        ("\u5f80\u4e0a\u4e00\u70b9", "\u5411\u4e0a\u4e00\u70b9", "\u62ac\u9ad8\u4e00\u70b9", "up a bit"),
        {"dx": 0, "dy": 0, "dz": 1, "feed": 800},
    ),
    (
        ("\u5f80\u4e0b\u4e00\u70b9", "\u5411\u4e0b\u4e00\u70b9", "\u964d\u4f4e\u4e00\u70b9", "down a bit"),
        {"dx": 0, "dy": 0, "dz": -1, "feed": 800},
    ),
)


def _normalize_text(text: str) -> str:
    normalized = "".join(str(text or "").strip().lower().split())
    return re.sub(r"[\s,.;:!?，。！？、：；\"'“”‘’]", "", normalized)


def _strip_voice_prefix(text: str) -> str:
    normalized = _normalize_text(text)
    for prefix in ("\u5c0f\u667a", "\u4f60\u597d\u5c0f\u667a", "xiaozhi", "heyxiaozhi"):
        if normalized.startswith(prefix):
            normalized = normalized[len(prefix) :]
            break
    return normalized


def resolve_direct_device_tool(
    text: str,
    has_tool: Optional[ToolPredicate] = None,
) -> Optional[Dict[str, Dict[str, object]]]:
    normalized = _normalize_text(text)
    if not normalized:
        return None

    for tokens, arguments in MOVE_REL_TOOL_RULES:
        if any(_normalize_text(token) in normalized for token in tokens):
            if has_tool is not None and not has_tool(MOVE_REL_TOOL_NAME):
                return None
            return {
                "function_call": {
                    "name": MOVE_REL_TOOL_NAME,
                    "arguments": dict(arguments),
                }
            }

    for _, tool_name, tokens in DIRECT_DEVICE_TOOL_RULES:
        if any(token.lower().replace(" ", "") in normalized for token in tokens):
            if has_tool is not None and not has_tool(tool_name):
                return None
            return {
                "function_call": {
                    "name": tool_name,
                    "arguments": {},
                }
            }
    return None


def resolve_voice_task_intent(text: str) -> Optional[Dict[str, object]]:
    normalized = _strip_voice_prefix(text)
    if not normalized:
        return None

    write_match = re.match(r"^(?:\u8bf7)?(?:\u5e2e\u6211)?(?:\u5199\u4e00\u4e0b|\u5199\u51fa|\u5199)(.+)$", normalized)
    if write_match:
        content = write_match.group(1).strip()
        if content:
            return {
                "task": {
                    "capability": "write_text",
                    "source": "voice",
                    "params": {
                        "text": content,
                        "font_id": DEFAULT_WRITE_TEXT_FONT_ID,
                    },
                }
            }

    draw_match = re.match(r"^(?:\u8bf7)?(?:\u5e2e\u6211)?(?:\u753b\u4e00\u4e2a|\u753b\u4e00\u53ea|\u753b\u4e00\u4e0b|\u753b)(.+)$", normalized)
    if draw_match:
        prompt = draw_match.group(1).strip()
        if prompt:
            return {
                "task": {
                    "capability": "draw_generated",
                    "source": "voice",
                    "params": {
                        "prompt": prompt,
                    },
                }
            }
    return None
