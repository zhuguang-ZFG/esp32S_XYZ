import asyncio
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
    run_fake_u8_script,
)


class MemoryTransport:
    def __init__(self, responses):
        self.sent = []
        self.responses = list(responses)

    async def send_json(self, payload):
        self.sent.append(payload)

    async def receive_json(self):
        if not self.responses:
            raise AssertionError("no response queued")
        return self.responses.pop(0)


class TestFakeLimaU8(unittest.TestCase):
    def test_arg_parser_defaults(self):
        args = build_arg_parser().parse_args([])

        self.assertEqual(args.url, "ws://127.0.0.1:8080/device/v1/ws")
        self.assertEqual(args.token, "test-device-token")
        self.assertEqual(args.device_id, "dev-1")
        self.assertEqual(args.transcript, "写你好")

    def test_hello_frame_uses_lima_protocol(self):
        frame = build_hello(FakeU8Config(device_id="dev-test", fw_rev="u8-test"))

        self.assertEqual(
            frame,
            {
                "type": "hello",
                "protocol": "lima-device-v1",
                "device_id": "dev-test",
                "fw_rev": "u8-test",
                "capabilities": ["run_path", "device_info", "self_check"],
            },
        )

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
                    "params": {"feed": 900, "path": [{"x": 0, "y": 0, "z": 0}]},
                },
                {"type": "motion_event_ack", "task_id": "task-1", "phase": "progress"},
                {"type": "motion_event_ack", "task_id": "task-1", "phase": "done"},
            ]
        )

        received = asyncio.run(run_fake_u8_script(transport, FakeU8Config(transcript="画一个星星")))

        self.assertEqual([frame["type"] for frame in received], [
            "hello_ack",
            "heartbeat_ack",
            "motion_task",
            "motion_event_ack",
            "motion_event_ack",
        ])
        self.assertEqual([frame["type"] for frame in transport.sent], [
            "hello",
            "heartbeat",
            "transcript",
            "motion_event",
            "motion_event",
        ])
        self.assertEqual(transport.sent[2]["text"], "画一个星星")
        self.assertEqual(transport.sent[3]["phase"], "progress")
        self.assertEqual(transport.sent[4]["phase"], "done")

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


if __name__ == "__main__":
    unittest.main()
