#!/usr/bin/env python3
"""Fake U8 client for the LiMa direct Device Gateway.

The core client is transport-injected so unit tests do not need a real network
or the optional ``websockets`` package. The CLI imports websockets only when it
is actually asked to connect to a running LiMa server.
"""

from __future__ import annotations

import argparse
import asyncio
import inspect
import json
from dataclasses import dataclass, field
from typing import Any, Protocol

from fake_lima_u8.route_policy_consumer import (
    attach_route_policy_evidence,
    parse_route_policy,
    record_route_policy_consumed,
)

PROTOCOL_VERSION = "lima-device-v1"
DEFAULT_CAPABILITIES = ["run_path", "device_info", "self_check"]
AUDIO_SAMPLE_RATE = 16000
AUDIO_SAMPLE_WIDTH = 2  # 16-bit
AUDIO_CHANNELS = 1
AUDIO_CHUNK_MS = 30  # 30ms per chunk
AUDIO_CHUNK_BYTES = int(AUDIO_SAMPLE_RATE * AUDIO_SAMPLE_WIDTH * AUDIO_CHANNELS * AUDIO_CHUNK_MS / 1000)


def _generate_silence_pcm(duration_ms: int) -> bytes:
    """Generate silent PCM data for the given duration."""
    num_samples = int(AUDIO_SAMPLE_RATE * duration_ms / 1000)
    return b"\x00\x00" * num_samples


def _generate_tone_pcm(frequency: int, duration_ms: int, amplitude: float = 0.3) -> bytes:
    """Generate a simple sine wave tone for testing."""
    import math
    import struct

    num_samples = int(AUDIO_SAMPLE_RATE * duration_ms / 1000)
    data = bytearray()
    for i in range(num_samples):
        t = i / AUDIO_SAMPLE_RATE
        sample = int(amplitude * 32767 * math.sin(2 * math.pi * frequency * t))
        data.extend(struct.pack("<h", max(-32768, min(32767, sample))))
    return bytes(data)


class JsonTransport(Protocol):
    async def send_json(self, payload: dict[str, Any]) -> None:
        ...

    async def receive_json(self) -> dict[str, Any]:
        ...


class BinaryTransport(Protocol):
    """Extension for binary frame (PCM audio) support."""

    async def send_bytes(self, data: bytes) -> None:
        ...

    async def receive_bytes(self) -> bytes:
        ...


@dataclass
class FakeU8Config:
    url: str = "ws://127.0.0.1:8080/device/v1/ws"
    token: str = "test-device-token"
    device_id: str = "dev-1"
    fw_rev: str = "fake-u8-lima-0.1.0"
    transcript: str = "hello"
    uptime_ms: int = 1
    capabilities: list[str] = field(default_factory=lambda: list(DEFAULT_CAPABILITIES + ["audio"]))
    artifact_dir: str = "device_artifacts"


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


def build_motion_failure_event(
    *,
    device_id: str,
    task_id: str,
    error_code: str,
    reason: str = "",
) -> dict[str, Any]:
    return {
        "type": "motion_event",
        "session_id": device_id,
        "device_id": device_id,
        "task_id": task_id,
        "phase": "failed",
        "error": {"code": error_code, "reason": reason or error_code},
    }


def assert_frame_type(frame: dict[str, Any], expected_type: str) -> dict[str, Any]:
    actual = frame.get("type")
    if actual != expected_type:
        raise RuntimeError(f"expected {expected_type}, got {actual}: {frame}")
    return frame


def _consume_route_policy(
    motion_task: dict[str, Any],
    config: FakeU8Config,
    *,
    scenario: str,
) -> tuple[dict[str, Any], dict[str, Any]]:
    route_policy = parse_route_policy(motion_task)
    task_id = str(motion_task.get("task_id", ""))
    if not task_id:
        raise RuntimeError(f"motion_task missing task_id: {motion_task}")
    consumed = record_route_policy_consumed(
        device_id=config.device_id,
        task_id=task_id,
        route_policy=route_policy,
        artifact_dir=config.artifact_dir,
        scenario=scenario,
    )
    return route_policy, consumed


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

    route_policy, consumed = _consume_route_policy(motion_task, config, scenario="success")
    received.append(consumed)
    task_id = str(motion_task["task_id"])

    await transport.send_json(build_motion_event(device_id=config.device_id, task_id=task_id, phase="progress", percent=50))
    progress_ack = assert_frame_type(await transport.receive_json(), "motion_event_ack")
    received.append(progress_ack)

    done_event = attach_route_policy_evidence(
        build_motion_event(device_id=config.device_id, task_id=task_id, phase="done", percent=100),
        route_policy,
    )
    await transport.send_json(done_event)
    done_ack = assert_frame_type(await transport.receive_json(), "motion_event_ack")
    received.append(done_ack)

    return received


class WebsocketsTransport:
    def __init__(self, websocket: Any):
        self.websocket = websocket

    async def send_json(self, payload: dict[str, Any]) -> None:
        await self.websocket.send(json.dumps(payload, ensure_ascii=False, separators=(",", ":")))

    async def receive_json(self) -> dict[str, Any]:
        return await _recv_json_or_skip_binary(self)

    async def send_bytes(self, data: bytes) -> None:
        await self.websocket.send(data)

    async def receive_bytes(self) -> bytes:
        raw = await self.websocket.recv()
        if not isinstance(raw, bytes):
            raise RuntimeError(f"expected bytes, got text: {raw[:80]!r}")
        return raw


async def run_websocket_client(config: FakeU8Config) -> list[dict[str, Any]]:
    try:
        import websockets
    except ImportError as exc:
        raise RuntimeError("Install websockets to run the fake LiMa U8 CLI") from exc

    headers = {"Authorization": f"Bearer {config.token}"}
    async with websockets.connect(config.url, **_websocket_header_kwargs(websockets.connect, headers)) as websocket:
        return await run_fake_u8_script(WebsocketsTransport(websocket), config)


async def run_fake_u8_failure_script(
    transport: JsonTransport,
    config: FakeU8Config,
    error_code: str = "E_MISSING_PATH",
) -> list[dict[str, Any]]:
    """Run a fake U8 loop that sends a failure event instead of done."""
    received: list[dict[str, Any]] = []

    await transport.send_json(build_hello(config))
    hello_ack = assert_frame_type(await transport.receive_json(), "hello_ack")
    received.append(hello_ack)

    await transport.send_json(build_transcript(config))
    motion_task = assert_frame_type(await transport.receive_json(), "motion_task")
    received.append(motion_task)

    route_policy, consumed = _consume_route_policy(motion_task, config, scenario="failure")
    received.append(consumed)
    task_id = str(motion_task["task_id"])

    await transport.send_json(build_motion_event(device_id=config.device_id, task_id=task_id, phase="accepted"))
    received.append(assert_frame_type(await transport.receive_json(), "motion_event_ack"))

    failure_event = attach_route_policy_evidence(
        build_motion_failure_event(
            device_id=config.device_id,
            task_id=task_id,
            error_code=error_code,
            reason=f"fake-U8 simulated {error_code}",
        ),
        route_policy,
    )
    await transport.send_json(failure_event)
    received.append(assert_frame_type(await transport.receive_json(), "motion_event_ack"))

    return received


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Fake U8 client for LiMa /device/v1/ws")
    parser.add_argument("--url", default="ws://127.0.0.1:8080/device/v1/ws")
    parser.add_argument("--token", default="test-device-token")
    parser.add_argument("--device-id", default="dev-1")
    parser.add_argument("--fw-rev", default="fake-u8-lima-0.1.0")
    parser.add_argument("--transcript", default="hello")
    parser.add_argument("--uptime-ms", type=int, default=1)
    parser.add_argument("--artifact-dir", default="device_artifacts")
    parser.add_argument("--test", default="success", choices=("success", "failure", "audio"))
    parser.add_argument("--fail-with", default="E_MISSING_PATH")
    return parser


def config_from_args(args: argparse.Namespace) -> FakeU8Config:
    return FakeU8Config(
        url=args.url,
        token=args.token,
        device_id=args.device_id,
        fw_rev=args.fw_rev,
        transcript=args.transcript,
        uptime_ms=args.uptime_ms,
        artifact_dir=args.artifact_dir,
    )


async def run_failure_websocket_client(config: FakeU8Config, error_code: str) -> list[dict[str, Any]]:
    try:
        import websockets
    except ImportError as exc:
        raise RuntimeError("Install websockets to run the fake LiMa U8 CLI") from exc

    headers = {"Authorization": f"Bearer {config.token}"}
    async with websockets.connect(config.url, **_websocket_header_kwargs(websockets.connect, headers)) as websocket:
        return await run_fake_u8_failure_script(WebsocketsTransport(websocket), config, error_code)


async def run_fake_u8_reconnect_script(
    transport_factory,
    config: FakeU8Config,
) -> list[dict[str, Any]]:
    """Run a fake U8 loop that disconnects mid-task and reconnects."""
    received: list[dict[str, Any]] = []
    t1 = await transport_factory()
    await t1.send_json(build_hello(config))
    hello_ack1 = assert_frame_type(await t1.receive_json(), "hello_ack")
    received.append(hello_ack1)
    await t1.send_json(build_transcript(config, request_id="reconnect-req-1"))
    motion_task = assert_frame_type(await t1.receive_json(), "motion_task")
    received.append(motion_task)
    route_policy, consumed = _consume_route_policy(motion_task, config, scenario="reconnect")
    received.append(consumed)
    task_id = str(motion_task["task_id"])
    await t1.send_json(build_motion_event(device_id=config.device_id, task_id=task_id, phase="accepted"))
    received.append(assert_frame_type(await t1.receive_json(), "motion_event_ack"))
    if hasattr(t1, "close"):
        await t1.close()
    await asyncio.sleep(0.5)
    t2 = await transport_factory()
    await t2.send_json(build_hello(config))
    hello_ack2 = assert_frame_type(await t2.receive_json(), "hello_ack")
    received.append(hello_ack2)
    return received


async def run_websocket_reconnect_client(config: FakeU8Config) -> list[dict[str, Any]]:
    try:
        import websockets
    except ImportError as exc:
        raise RuntimeError("Install websockets to run the fake LiMa U8 CLI") from exc
    headers = {"Authorization": f"Bearer {config.token}"}
    url = config.url

    async def _factory():
        ws = await websockets.connect(url, **_websocket_header_kwargs(websockets.connect, headers))
        return WebsocketsTransport(ws)

    return await run_fake_u8_reconnect_script(_factory, config)


def _websocket_header_kwargs(connect_func: Any, headers: dict[str, str]) -> dict[str, dict[str, str]]:
    params = inspect.signature(connect_func).parameters
    if "additional_headers" in params:
        return {"additional_headers": headers}
    return {"extra_headers": headers}


async def _recv_json_or_skip_binary(transport: WebsocketsTransport) -> dict[str, Any]:
    """Receive JSON from WebSocket, silently skipping any binary frames."""
    while True:
        raw = await transport.websocket.recv()
        if isinstance(raw, bytes):
            # Skip binary frames (audio replies) during JSON receive
            continue
        data = json.loads(raw)
        if not isinstance(data, dict):
            raise RuntimeError(f"expected JSON object, got: {data!r}")
        return data


def main(argv: list[str] | None = None) -> int:
    args = build_arg_parser().parse_args(argv)
    cfg = config_from_args(args)
    if args.test == "failure":
        frames = asyncio.run(run_failure_websocket_client(cfg, args.fail_with))
        print(json.dumps({"ok": True, "test": "failure", "error_code": args.fail_with, "received": frames}, ensure_ascii=False, indent=2))
        return 0
    if args.test == "audio":
        frames = asyncio.run(run_audio_streaming_client(cfg))
        print(json.dumps({"ok": True, "test": "audio", "received": frames}, ensure_ascii=False, indent=2))
        return 0
    frames = asyncio.run(run_websocket_client(cfg))
    print(json.dumps({"ok": True, "received": frames}, ensure_ascii=False, indent=2))
    return 0


async def run_audio_streaming_client(config: FakeU8Config) -> list[dict[str, Any]]:
    """Run fake U8 with audio streaming: send PCM chunks, receive voice responses."""
    try:
        import websockets
    except ImportError as exc:
        raise RuntimeError("Install websockets to run the fake LiMa U8 CLI") from exc

    headers = {"Authorization": f"Bearer {config.token}"}
    async with websockets.connect(config.url, **_websocket_header_kwargs(websockets.connect, headers)) as websocket:
        transport = WebsocketsTransport(websocket)
        received: list[dict[str, Any]] = []

        # 1. Send hello
        await transport.send_json(build_hello(config))
        hello_ack = await transport.receive_json()
        assert_frame_type(hello_ack, "hello_ack")
        received.append(hello_ack)

        # 2. Send audio chunks (simulated PCM tone)
        tone = _generate_tone_pcm(440, 500)  # 440Hz, 500ms
        silence = _generate_silence_pcm(1500)  # 1500ms silence (trigger utterance end)
        audio_data = tone + silence

        # Send audio in chunks
        offset = 0
        while offset < len(audio_data):
            chunk = audio_data[offset : offset + AUDIO_CHUNK_BYTES]
            if chunk:
                await transport.send_bytes(chunk)
            offset += AUDIO_CHUNK_BYTES
            await asyncio.sleep(0.01)

        # 3. Collect voice_status and audio_reply frames
        try:
            while True:
                raw = await asyncio.wait_for(websocket.recv(), timeout=5.0)
                if isinstance(raw, bytes):
                    received.append({"type": "audio_reply_binary", "bytes": len(raw)})
                else:
                    frame = json.loads(raw)
                    received.append(frame)
                    if frame.get("type") == "voice_status" and frame.get("status") == "idle":
                        break
        except asyncio.TimeoutError:
            received.append({"type": "timeout", "message": "audio pipeline timeout"})

        return received


if __name__ == "__main__":
    raise SystemExit(main())
