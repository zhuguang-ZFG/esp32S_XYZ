#!/usr/bin/env python3
"""Socket-level fake U1 integration tests for the CI fake-integration job."""

from __future__ import annotations

import json
import socket
import threading
import unittest
import urllib.request
from http.server import HTTPServer
from pathlib import Path
from typing import Any, Dict

from jsonschema import Draft202012Validator

from tools.fake_device_server import app as fake_device_server_app
from tools.fake_device_server.app import FakeDeviceServerHandler
from tools.fake_u1.app import FakeU1Simulator, FakeU1TCPServer


ROOT = Path(__file__).resolve().parents[2]
EDGE_D = ROOT / "docs" / "schemas" / "edge_d"


def load_schema(name: str) -> dict:
    return json.loads((EDGE_D / name).read_text(encoding="utf-8-sig"))


class FakeU1SocketIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.simulator = FakeU1Simulator()
        cls.server = FakeU1TCPServer(("127.0.0.1", 0), cls.simulator)
        cls.host, cls.port = cls.server.server_address
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

        fake_device_server_app.FAKE_U1_HOST = cls.host
        fake_device_server_app.FAKE_U1_PORT = cls.port
        FakeDeviceServerHandler.business_base_url = ""
        FakeDeviceServerHandler.internal_token = ""
        cls.device_server = HTTPServer(("127.0.0.1", 0), FakeDeviceServerHandler)
        cls.device_host, cls.device_port = cls.device_server.server_address
        cls.device_thread = threading.Thread(target=cls.device_server.serve_forever, daemon=True)
        cls.device_thread.start()

        cls.ack_schema = Draft202012Validator(load_schema("ack.schema.json"))
        cls.device_info_schema = Draft202012Validator(load_schema("device_info.schema.json"))
        cls.status_schema = Draft202012Validator(load_schema("status.schema.json"))
        cls.result_schema = Draft202012Validator(load_schema("result.schema.json"))
        cls.error_schema = Draft202012Validator(load_schema("error.schema.json"))

    @classmethod
    def tearDownClass(cls) -> None:
        cls.device_server.shutdown()
        cls.device_server.server_close()
        cls.device_thread.join(timeout=5)
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=5)

    def setUp(self) -> None:
        self.simulator.state = FakeU1Simulator().state
        self.simulator.inject_codes.clear()

    def send(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        with socket.create_connection((self.host, self.port), timeout=5) as sock:
            with sock.makefile("rwb") as fh:
                frame = "@" + json.dumps(payload, separators=(",", ":")) + "\n"
                fh.write(frame.encode("utf-8"))
                fh.flush()
                line = fh.readline().decode("utf-8").strip()
        if line.startswith("@"):
            line = line[1:]
        return json.loads(line)

    def post_device_server(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        req = urllib.request.Request(
            f"http://{self.device_host}:{self.device_port}{path}",
            data=json.dumps(payload, separators=(",", ":")).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=5) as response:
            return json.loads(response.read().decode("utf-8"))

    def assert_schema_valid(self, schema: Draft202012Validator, payload: dict) -> None:
        errors = sorted(schema.iter_errors(payload), key=lambda error: list(error.path))
        self.assertEqual(errors, [], [error.message for error in errors])

    def test_get_status_home_move_happy_path(self) -> None:
        status = self.send({"msg_id": "ci-1", "task_id": "status", "cmd": "GET_STATUS"})
        self.assertEqual(status["type"], "status")
        self.assertFalse(status["homed"])
        self.assert_schema_valid(self.status_schema, status)

        home = self.send({"msg_id": "ci-2", "task_id": "home", "cmd": "HOME"})
        self.assertEqual(home["type"], "result")
        self.assertEqual(home["result"], "DONE")
        self.assert_schema_valid(self.result_schema, home)

        move = self.send({"msg_id": "ci-3", "task_id": "move", "cmd": "MOVE", "x": 10, "y": 5, "z": 1, "feed": 1200})
        self.assertEqual(move["type"], "result")
        self.assertEqual(move["result"], "DONE")
        self.assert_schema_valid(self.result_schema, move)

        moved_status = self.send({"msg_id": "ci-4", "task_id": "status2", "cmd": "GET_STATUS"})
        self.assertEqual(moved_status["position"], {"x": 10.0, "y": 5.0, "z": 1.0})
        self.assert_schema_valid(self.status_schema, moved_status)

    def test_path_pause_resume_and_stop(self) -> None:
        self.send({"msg_id": "ci-5", "task_id": "home", "cmd": "HOME"})

        begin = self.send({"msg_id": "ci-6", "task_id": "path", "cmd": "PATH_BEGIN", "total_segments": 1})
        self.assertEqual(begin["type"], "ack")
        self.assertTrue(begin["accepted"])
        self.assert_schema_valid(self.ack_schema, begin)

        segment = self.send(
            {
                "msg_id": "ci-7",
                "task_id": "path",
                "cmd": "PATH_SEG",
                "segment_index": 0,
                "segment_cmd": "L",
                "x": 20,
                "y": 8,
                "z": 2,
                "feed": 1000,
            }
        )
        self.assertEqual(segment["type"], "ack")
        self.assert_schema_valid(self.ack_schema, segment)

        paused = self.send({"msg_id": "ci-8", "task_id": "path", "cmd": "PAUSE"})
        self.assertEqual(paused["state"], "PAUSED")
        self.assert_schema_valid(self.ack_schema, paused)

        resumed = self.send({"msg_id": "ci-9", "task_id": "path", "cmd": "RESUME"})
        self.assertEqual(resumed["state"], "RUNNING")
        self.assert_schema_valid(self.ack_schema, resumed)

        stopped = self.send({"msg_id": "ci-10", "task_id": "path", "cmd": "STOP"})
        self.assertEqual(stopped["result"], "CANCELLED")
        self.assert_schema_valid(self.result_schema, stopped)

    def test_error_paths_are_schema_valid(self) -> None:
        not_homed = self.send({"msg_id": "ci-11", "task_id": "move-before-home", "cmd": "MOVE", "x": 1})
        self.assertEqual(not_homed["error_code"], "E001")
        self.assert_schema_valid(self.error_schema, not_homed)

        self.send({"msg_id": "ci-12", "task_id": "home", "cmd": "HOME"})
        soft_limit = self.send({"msg_id": "ci-13", "task_id": "soft-limit", "cmd": "MOVE", "x": 201})
        self.assertEqual(soft_limit["error_code"], "E002")
        self.assert_schema_valid(self.error_schema, soft_limit)

        self.simulator.queue_injection("E005")
        hard_limit = self.send({"msg_id": "ci-14", "task_id": "hard-limit", "cmd": "MOVE", "x": 1})
        self.assertEqual(hard_limit["error_code"], "E005")
        self.assert_schema_valid(self.error_schema, hard_limit)

    def test_get_device_info_current_contract(self) -> None:
        response = self.send({"msg_id": "ci-15", "cmd": "GET_DEVICE_INFO"})
        self.assertEqual(response["type"], "result")
        self.assertEqual(response["task_id"], "device-info")
        self.assertEqual(response["model"], "DLC Motor Control P1 XYYZ")
        self.assert_schema_valid(self.device_info_schema, response)

    def test_fake_device_server_get_device_info_motion_task(self) -> None:
        response = self.post_device_server(
            "/internal/v1/motion_task",
            {"device_id": "dev-ci", "task_id": "task-info", "capability": "get_device_info", "params": {}},
        )
        self.assertEqual(response["code"], 0)
        info = response["data"]["device_info"]
        self.assertEqual(info["device_id"], "dev-ci")
        self.assertEqual(info["task_id"], "task-info")
        self.assertEqual(info["model"], "DLC Motor Control P1 XYYZ")
        self.assertEqual(info["workspace_mm"], {"x": 200.0, "y": 150.0, "z": 50.0})

    def test_fake_device_server_run_path_motion_task_drives_fake_u1(self) -> None:
        self.send({"msg_id": "ci-16", "task_id": "home", "cmd": "HOME"})

        response = self.post_device_server(
            "/internal/v1/motion_task",
            {
                "device_id": "dev-ci",
                "task_id": "task-path",
                "capability": "run_path",
                "params": {
                    "feed": 900,
                    "path": [
                        {"cmd": "M", "x": 0, "y": 0, "z": 0},
                        {"cmd": "L", "x": 10, "y": 0, "z": 0},
                        {"cmd": "L", "x": 10, "y": 10, "z": 0},
                    ],
                },
            },
        )

        self.assertEqual(response["code"], 0)
        self.assertEqual(response["data"]["task_id"], "task-path")
        self.assertEqual(response["data"]["status"], "IDLE")

        status = self.send({"msg_id": "ci-17", "task_id": "status-after-path", "cmd": "GET_STATUS"})
        self.assertEqual(status["position"], {"x": 10.0, "y": 10.0, "z": 0.0})
        self.assert_schema_valid(self.status_schema, status)


if __name__ == "__main__":
    unittest.main()
