#!/usr/bin/env python3
"""Fake U8 client for the LiMa direct Device Gateway.

The core client is transport-injected so unit tests do not need a real network
or the optional ``websockets`` package. The CLI imports websockets only when it
is actually asked to connect to a running LiMa server.
"""

from __future__ import annotations

import argparse
import asyncio
import json
from dataclasses import dataclass, field
from typing import Any, Protocol


PROTOCOL_VERSION = "lima-device-v1"
DEFAULT_CAPABILITIES = ["run_path", "device_info", "self_check"]


class JsonTransport(Protocol):
    async def send_json(self, payload: dict[str, Any]) -> None:
        ...

    async def receive_json(self) -> dict[str, Any]:
        ...


@dataclass
class FakeU8Config:
    url: str = "ws://127.0.0.1:8080/device/v1/ws"
    token: str = "test-device-token"
    device_id: str = "dev-1"
    fw_rev: str = "fake-u8-lima-0.1.0"
    transcript: str = "写你好"
    uptime_ms: int = 1
    capabilities: list[str] = field(default_factory=lambda: list(DEFAULT_CAPABILITIES))


def build_hello(config: FakeU8Config) -> dict[str, Any]:
    return {
        "type": "hello",
        "protocol": PROTOCOL_VERSION,
        "device_id": config.device_id,
        "fw_rev": config.fw_rev,
        "capabilities": list(config.capabilities),
    }


def build_heartbeat(config: FakeU8Config) -> dict[str, Any]:
    return {
        "type": "heartbeat",
        "device_id": config.device_id,
        "uptime_ms": config.uptime_ms,
    }


def build_transcript(config: FakeU8Config, request_id: str = "fake-u8-req-1") -> dict[str, Any]:
    return {
        "type": "transcript",
        "device_id": config.device_id,
        "text": config.transcript,
        "request_id": request_id,
    }


def build_motion_event(
    *,
    device_id: str,
    task_id: str,
    phase: str,
    percent: int | None = None,
) -> dict[str, Any]:
    event: dict[str, Any] = {
        "type": "motion_event",
        "session_id": device_id,
        "device_id": device_id,
        "task_id": task_id,
        "phase": phase,
    }
    if percent is not None:
        event["progress"] = {"percent": percent}
    return event


def assert_frame_type(frame: dict[str, Any], expected_type: str) -> dict[str, Any]:
    actual = frame.get("type")
    if actual != expected_type:
        raise RuntimeError(f"expected {expected_type}, got {actual}: {frame}")
    return frame


async def run_fake_u8_script(transport: JsonTransport, config: FakeU8Config) -> list[dict[str, Any]]:
    """Run a deterministic fake U8 hello/heartbeat/transcript/motion loop."""
    received: list[dict[str, Any]] = []

    await transport.send_json(build_hello(config))
    hello_ack = assert_frame_type(await transport.receive_json(), "hello_ack")
    received.append(hello_ack)

    await transport.send_json(build_heartbeat(config))
    heartbeat_ack = assert_frame_type(await transport.receive_json(), "heartbeat_ack")
    received.append(heartbeat_ack)

    await transport.send_json(build_transcript(config))
    motion_task = assert_frame_type(await transport.receive_json(), "motion_task")
    if motion_task.get("capability") != "run_path":
        raise RuntimeError(f"expected run_path motion_task, got: {motion_task}")
    received.append(motion_task)

    task_id = str(motion_task.get("task_id", ""))
    if not task_id:
        raise RuntimeError(f"motion_task missing task_id: {motion_task}")

    await transport.send_json(build_motion_event(device_id=config.device_id, task_id=task_id, phase="progress", percent=50))
    progress_ack = assert_frame_type(await transport.receive_json(), "motion_event_ack")
    received.append(progress_ack)

    await transport.send_json(build_motion_event(device_id=config.device_id, task_id=task_id, phase="done", percent=100))
    done_ack = assert_frame_type(await transport.receive_json(), "motion_event_ack")
    received.append(done_ack)

    return received


class WebsocketsTransport:
    def __init__(self, websocket: Any):
        self.websocket = websocket

    async def send_json(self, payload: dict[str, Any]) -> None:
        await self.websocket.send(json.dumps(payload, ensure_ascii=False, separators=(",", ":")))

    async def receive_json(self) -> dict[str, Any]:
        raw = await self.websocket.recv()
        if isinstance(raw, bytes):
            raw = raw.decode("utf-8")
        data = json.loads(raw)
        if not isinstance(data, dict):
            raise RuntimeError(f"expected JSON object, got: {data!r}")
        return data


async def run_websocket_client(config: FakeU8Config) -> list[dict[str, Any]]:
    try:
        import websockets
    except ImportError as exc:
        raise RuntimeError("Install websockets to run the fake LiMa U8 CLI") from exc

    headers = {"Authorization": f"Bearer {config.token}"}
    async with websockets.connect(config.url, extra_headers=headers) as websocket:
        return await run_fake_u8_script(WebsocketsTransport(websocket), config)


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Fake U8 client for LiMa /device/v1/ws")
    parser.add_argument("--url", default="ws://127.0.0.1:8080/device/v1/ws")
    parser.add_argument("--token", default="test-device-token")
    parser.add_argument("--device-id", default="dev-1")
    parser.add_argument("--fw-rev", default="fake-u8-lima-0.1.0")
    parser.add_argument("--transcript", default="写你好")
    parser.add_argument("--uptime-ms", type=int, default=1)
    return parser


def config_from_args(args: argparse.Namespace) -> FakeU8Config:
    return FakeU8Config(
        url=args.url,
        token=args.token,
        device_id=args.device_id,
        fw_rev=args.fw_rev,
        transcript=args.transcript,
        uptime_ms=args.uptime_ms,
    )


def main(argv: list[str] | None = None) -> int:
    args = build_arg_parser().parse_args(argv)
    frames = asyncio.run(run_websocket_client(config_from_args(args)))
    print(json.dumps({"ok": True, "received": frames}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

