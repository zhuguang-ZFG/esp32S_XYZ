#!/usr/bin/env python3
"""Deterministic fake AI provider for M0c.3."""

from __future__ import annotations

import argparse
import io
import json
import wave
from typing import Any, Dict, Optional


DEFAULT_TRANSCRIPT = "小智回原点"


def fake_asr(text: str = "") -> Dict[str, Any]:
    transcript = text.strip() or DEFAULT_TRANSCRIPT
    return {"type": "asr", "transcript": transcript, "confidence": 1.0}


def fake_intent(text: str, device_id: str = "dev_fake", session_id: str = "session_fake") -> Dict[str, Any]:
    normalized = text.strip().lower()
    intent: Optional[str] = None
    params: Dict[str, Any] = {}

    if any(token in normalized for token in ("归零", "回原点", "home")):
        intent = "home"
    elif any(token in normalized for token in ("暂停", "pause")):
        intent = "pause"
    elif any(token in normalized for token in ("继续", "resume")):
        intent = "resume"
    elif any(token in normalized for token in ("停止", "stop")):
        intent = "stop"
    elif any(token in normalized for token in ("型号", "设备信息", "device info")):
        intent = "get_device_info"
    elif any(token in normalized for token in ("左", "右", "上", "下", "抬高", "降低", "move")):
        intent = "move_relative"
        params = {"dx": 0.0, "dy": 0.0, "dz": 0.0}
        if "左" in normalized:
            params["dx"] = -1.0
        elif "右" in normalized:
            params["dx"] = 1.0
        if "上" in normalized or "抬高" in normalized:
            params["dz"] = 1.0
        elif "下" in normalized or "降低" in normalized:
            params["dz"] = -1.0

    if intent is None:
        return {
            "device_id": device_id,
            "session_id": session_id,
            "voice_text": text,
            "confidence": 0.5,
        }

    return {
        "device_id": device_id,
        "session_id": session_id,
        "intent": intent,
        "params": params,
        "voice_text": text,
        "confidence": 1.0,
    }


def fake_tts_wav(text: str, duration_ms: int = 200, sample_rate: int = 16000) -> bytes:
    del text
    frames = max(1, int(sample_rate * duration_ms / 1000))
    silence = b"\x00\x00" * frames
    buf = io.BytesIO()
    with wave.open(buf, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(sample_rate)
        wav.writeframes(silence)
    return buf.getvalue()


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="fake AI provider - M0c.3")
    sub = parser.add_subparsers(dest="command", required=True)

    asr = sub.add_parser("asr", help="emit a deterministic ASR transcript")
    asr.add_argument("--text", default="", help="override transcript text")

    intent = sub.add_parser("intent", help="classify text into a fake device/chat intent")
    intent.add_argument("--text", required=True)
    intent.add_argument("--device-id", default="dev_fake")
    intent.add_argument("--session-id", default="session_fake")

    tts = sub.add_parser("tts", help="write a short silent WAV")
    tts.add_argument("--text", default="")
    tts.add_argument("--output", required=True)
    tts.add_argument("--duration-ms", default=200, type=int)
    return parser


def main() -> None:
    args = build_arg_parser().parse_args()
    if args.command == "asr":
        print(json.dumps(fake_asr(args.text), ensure_ascii=False, separators=(",", ":")))
    elif args.command == "intent":
        print(
            json.dumps(
                fake_intent(args.text, device_id=args.device_id, session_id=args.session_id),
                ensure_ascii=False,
                separators=(",", ":"),
            )
        )
    elif args.command == "tts":
        with open(args.output, "wb") as fh:
            fh.write(fake_tts_wav(args.text, duration_ms=args.duration_ms))


if __name__ == "__main__":
    main()
