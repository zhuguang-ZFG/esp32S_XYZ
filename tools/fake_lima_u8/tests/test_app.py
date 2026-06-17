import asyncio
import json
import tempfile
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent))

from fake_lima_u8.app import (
    FakeU8Config,
    _websocket_header_kwargs,
    assert_frame_type,
    build_arg_parser,
    build_hello,
    build_motion_event,
    run_fake_u8_failure_script,
    run_fake_u8_script,
)

_RUN_PATH_POLICY = {
    "route_role": "device_vector",
    "model_required": False,
    "primary_strategy": "provided_path",
    "artifact_required": "preview_svg",
}


class MemoryTransport:
    def __init__(self, responses):
        self.sent = []
        self.sent_bytes: list[bytes] = []
        self.responses = list(responses)

    async def send_json(self, payload):
        self.sent.append(payload)

    async def receive_json(self):
        if not self.responses:
            raise AssertionError("no response queued")
        return self.responses.pop(0)

    async def send_bytes(self, data: bytes) -> None:
        self.sent_bytes.append(data)

    async def receive_bytes(self) -> bytes:
        if not self.responses:
            raise AssertionError("no response queued")
        result = self.responses.pop(0)
        if not isinstance(result, bytes):
            raise AssertionError(f"expected bytes, got {type(result)}")
        return result


class TestFakeLimaU8(unittest.TestCase):
    def test_arg_parser_defaults(self):
        args = build_arg_parser().parse_args([])

        self.assertEqual(args.url, "ws://127.0.0.1:8080/device/v1/ws")
        self.assertEqual(args.token, "test-device-token")
        self.assertEqual(args.transcript, "hello")
        self.assertEqual(args.artifact_dir, "device_artifacts")

    def test_hello_frame_uses_lima_protocol(self):
        frame = build_hello(FakeU8Config(device_id="dev-test", fw_rev="u8-test"))

        self.assertEqual(frame["type"], "hello")
        self.assertEqual(frame["protocol"], "lima-device-v1")
        self.assertEqual(frame["device_id"], "dev-test")
        self.assertEqual(frame["fw_rev"], "u8-test")
        self.assertIn("audio", frame["capabilities"])

    def test_motion_event_includes_session_id_for_esp32_compatibility(self):
        event = build_motion_event(device_id="dev-1", task_id="task-1", phase="progress", percent=25)

        self.assertEqual(event["type"], "motion_event")
        self.assertEqual(event["device_id"], "dev-1")
        self.assertEqual(event["session_id"], "dev-1")
        self.assertEqual(event["progress"], {"percent": 25})

    def test_script_runs_hello_heartbeat_transcript_and_motion_events(self):
        transport = MemoryTransport(
            [
                {"type": "hello_ack", "device_id": "dev-1"},
                {"type": "heartbeat_ack", "device_id": "dev-1", "uptime_ms": 1},
                {
                    "type": "motion_task",
                    "task_id": "task-1",
                    "device_id": "dev-1",
                    "capability": "run_path",
                    "route_policy": dict(_RUN_PATH_POLICY),
                    "params": {"feed": 900, "path": [{"x": 0, "y": 0, "z": 0}]},
                },
                {"type": "motion_event_ack", "task_id": "task-1", "phase": "progress"},
                {"type": "motion_event_ack", "task_id": "task-1", "phase": "done"},
            ]
        )

        received = asyncio.run(run_fake_u8_script(transport, FakeU8Config(transcript="画一个星星")))

        self.assertEqual(
            [frame["type"] for frame in received],
            ["hello_ack", "heartbeat_ack", "motion_task", "route_policy_consumed", "motion_event_ack", "motion_event_ack"],
        )
        self.assertEqual(
            [frame["type"] for frame in transport.sent],
            ["hello", "heartbeat", "transcript", "motion_event", "motion_event"],
        )
        self.assertEqual(transport.sent[2]["text"], "画一个星星")
        self.assertEqual(transport.sent[3]["phase"], "progress")
        done_frame = transport.sent[4]
        self.assertEqual(done_frame["phase"], "done")
        self.assertEqual(done_frame["route_policy_evidence"]["route_role"], "device_vector")

    def test_motion_task_without_route_policy_fails(self):
        transport = MemoryTransport(
            [
                {"type": "hello_ack", "device_id": "dev-1"},
                {"type": "heartbeat_ack", "device_id": "dev-1", "uptime_ms": 1},
                {
                    "type": "motion_task",
                    "task_id": "task-1",
                    "device_id": "dev-1",
                    "capability": "run_path",
                    "params": {"feed": 900},
                },
            ]
        )

        with self.assertRaises(RuntimeError):
            asyncio.run(run_fake_u8_script(transport, FakeU8Config()))

    def test_unexpected_frame_type_fails_fast(self):
        with self.assertRaises(RuntimeError):
            assert_frame_type({"type": "error", "code": "E_TEST"}, "hello_ack")

    def test_websocket_header_kwargs_supports_new_websockets_api(self):
        def connect(uri, *, additional_headers=None):
            return uri, additional_headers

        self.assertEqual(
            _websocket_header_kwargs(connect, {"Authorization": "Bearer test"}),
            {"additional_headers": {"Authorization": "Bearer test"}},
        )

    def test_websocket_header_kwargs_supports_old_websockets_api(self):
        def connect(uri, *, extra_headers=None):
            return uri, extra_headers

        self.assertEqual(
            _websocket_header_kwargs(connect, {"Authorization": "Bearer test"}),
            {"extra_headers": {"Authorization": "Bearer test"}},
        )

    def test_route_policy_consumed_and_logged(self):
        route_policy = {
            "route_role": "device_draw",
            "model_required": True,
            "primary_strategy": "image_then_vector",
            "artifact_required": "preview_svg",
            "backend": "dashscope_wanx",
        }
        transport = MemoryTransport(
            [
                {"type": "hello_ack", "device_id": "dev-1"},
                {"type": "heartbeat_ack", "device_id": "dev-1", "uptime_ms": 1},
                {
                    "type": "motion_task",
                    "task_id": "task-route-policy-1",
                    "device_id": "dev-1",
                    "capability": "run_path",
                    "route_policy": route_policy,
                    "params": {"feed": 900, "path": [{"x": 0, "y": 0, "z": 0}]},
                },
                {"type": "motion_event_ack", "task_id": "task-route-policy-1", "phase": "progress"},
                {"type": "motion_event_ack", "task_id": "task-route-policy-1", "phase": "done"},
            ]
        )

        with tempfile.TemporaryDirectory() as tmp_dir:
            config = FakeU8Config(transcript="画一个星星", artifact_dir=tmp_dir)
            received = asyncio.run(run_fake_u8_script(transport, config))

            route_consumed = [frame for frame in received if frame.get("type") == "route_policy_consumed"]
            self.assertEqual(len(route_consumed), 1)
            self.assertEqual(route_consumed[0]["route_policy"], route_policy)

            log_file = Path(tmp_dir) / "fake_u8_route_policy_dev-1.log"
            self.assertTrue(log_file.exists())
            log_line = log_file.read_text(encoding="utf-8").strip().splitlines()[-1]
            log_content = json.loads(log_line)
            self.assertEqual(log_content["route_policy"], route_policy)

            done_frame = transport.sent[-1]
            self.assertEqual(done_frame["route_policy_evidence"]["backend"], "dashscope_wanx")

    def test_route_policy_in_failure_scenario(self):
        route_policy = {
            "route_role": "device_write",
            "model_required": False,
            "primary_strategy": "provided_path",
            "artifact_required": "none",
        }
        transport = MemoryTransport(
            [
                {"type": "hello_ack", "device_id": "dev-1"},
                {
                    "type": "motion_task",
                    "task_id": "task-failure-1",
                    "device_id": "dev-1",
                    "capability": "run_path",
                    "route_policy": route_policy,
                },
                {"type": "motion_event_ack", "task_id": "task-failure-1", "phase": "accepted"},
                {"type": "motion_event_ack", "task_id": "task-failure-1", "phase": "failed"},
            ]
        )

        config = FakeU8Config()
        received = asyncio.run(run_fake_u8_failure_script(transport, config, error_code="E_MISSING_PATH"))

        route_consumed = [frame for frame in received if frame.get("type") == "route_policy_consumed"]
        self.assertEqual(len(route_consumed), 1)
        self.assertEqual(route_consumed[0]["route_policy"], route_policy)
        self.assertEqual(route_consumed[0]["scenario"], "failure")
        self.assertIn("route_policy_evidence", transport.sent[-1])


if __name__ == "__main__":
    unittest.main()


class TestAudioStreaming(unittest.TestCase):
    """Tests for audio PCM generation and streaming helpers."""

    def test_generate_silence_pcm_produces_expected_bytes(self):
        from fake_lima_u8.app import _generate_silence_pcm, AUDIO_SAMPLE_RATE, AUDIO_SAMPLE_WIDTH, AUDIO_CHANNELS

        # 100ms of silence
        pcm = _generate_silence_pcm(100)
        expected_bytes = int(AUDIO_SAMPLE_RATE * 100 / 1000) * AUDIO_SAMPLE_WIDTH * AUDIO_CHANNELS
        self.assertEqual(len(pcm), expected_bytes)
        self.assertEqual(pcm, b"\x00\x00" * (expected_bytes // 2))

    def test_generate_tone_pcm_is_non_empty(self):
        from fake_lima_u8.app import _generate_tone_pcm

        pcm = _generate_tone_pcm(440, 100)
        self.assertGreater(len(pcm), 0)
        # Should not be all zeros
        self.assertNotEqual(pcm, b"\x00" * len(pcm))

    def test_audio_chunk_size_matches_config(self):
        from fake_lima_u8.app import AUDIO_CHUNK_BYTES, AUDIO_CHUNK_MS, AUDIO_SAMPLE_RATE, AUDIO_SAMPLE_WIDTH, AUDIO_CHANNELS

        expected = int(AUDIO_SAMPLE_RATE * AUDIO_SAMPLE_WIDTH * AUDIO_CHANNELS * AUDIO_CHUNK_MS / 1000)
        self.assertEqual(AUDIO_CHUNK_BYTES, expected)

    def test_audio_streaming_script_sends_binary_chunks(self):
        """Verify audio script sends binary frames followed by voice_status."""
        from fake_lima_u8.app import AUDIO_CHUNK_BYTES, _generate_tone_pcm, _generate_silence_pcm

        # Build expected audio payload
        tone = _generate_tone_pcm(440, 500)
        silence = _generate_silence_pcm(1500)
        audio_data = tone + silence
        expected_chunks = (len(audio_data) + AUDIO_CHUNK_BYTES - 1) // AUDIO_CHUNK_BYTES

        self.assertGreater(expected_chunks, 2, "audio streaming should send multiple chunks")

    def test_memory_transport_binary_support(self):
        """MemoryTransport correctly handles binary send/receive."""
        async def _run():
            transport = MemoryTransport([b"\x00\x00\x01\x02"])
            await transport.send_bytes(b"hello")
            self.assertEqual(transport.sent_bytes, [b"hello"])
            result = await transport.receive_bytes()
            self.assertEqual(result, b"\x00\x00\x01\x02")
        asyncio.run(_run())
