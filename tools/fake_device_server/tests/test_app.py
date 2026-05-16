"""M0c.2 fake_device_server 单元测试。"""
import json
import threading
import unittest
import urllib.error
import urllib.request
from http.server import HTTPServer
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent))
from fake_device_server.app import (
    FakeDeviceServerHandler,
    build_arg_parser,
    device_info_report_from_u1_response,
    motion_task_to_u1_command,
    motion_task_to_u1_commands,
)


class TestArgParser(unittest.TestCase):
    def test_defaults(self):
        args = build_arg_parser().parse_args([])
        self.assertEqual(args.host, "127.0.0.1")
        self.assertEqual(args.port, 8100)
        self.assertEqual(args.fake_u1_host, "127.0.0.1")
        self.assertEqual(args.fake_u1_port, 7799)
        self.assertEqual(args.business_base_url, "")

    def test_custom_port(self):
        args = build_arg_parser().parse_args(["--port", "9200"])
        self.assertEqual(args.port, 9200)

    def test_business_url(self):
        args = build_arg_parser().parse_args(["--business-base-url", "http://localhost:8080"])
        self.assertEqual(args.business_base_url, "http://localhost:8080")


class TestMotionTaskMapping(unittest.TestCase):
    def test_get_status_keeps_task_id(self):
        cmd = motion_task_to_u1_command({"capability": "get_status", "task_id": "task-status"})
        self.assertEqual(cmd, {"msg_id": "1", "task_id": "task-status", "cmd": "GET_STATUS"})

    def test_get_device_info(self):
        cmd = motion_task_to_u1_command({"capability": "get_device_info", "task_id": "task-info"})
        self.assertEqual(cmd, {"msg_id": "1", "task_id": "task-info", "cmd": "GET_DEVICE_INFO"})

    def test_move_abs(self):
        cmd = motion_task_to_u1_command(
            {"capability": "move_abs", "task_id": "task-move", "params": {"x": 1, "y": 2, "z": 3, "feed": 400}}
        )
        self.assertEqual(
            cmd,
            {"msg_id": "1", "task_id": "task-move", "cmd": "MOVE", "x": 1, "y": 2, "z": 3, "feed": 400},
        )

    def test_move_rel_is_explicit_fake_capability(self):
        cmd = motion_task_to_u1_command(
            {"capability": "move_rel", "task_id": "task-rel", "params": {"dx": -1, "dy": 0, "dz": 1, "feed": 800}}
        )

        self.assertEqual(
            cmd,
            {"msg_id": "1", "task_id": "task-rel", "cmd": "MOVE_REL", "dx": -1, "dy": 0, "dz": 1, "feed": 800},
        )

    def test_control_capabilities(self):
        cases = {
            "pause": "PAUSE",
            "resume": "RESUME",
            "stop": "STOP",
            "estop": "ESTOP",
        }
        for capability, expected_cmd in cases.items():
            with self.subTest(capability=capability):
                cmd = motion_task_to_u1_command({"capability": capability, "task_id": "task-control"})
                self.assertEqual(cmd["cmd"], expected_cmd)

    def test_run_path_expands_to_edge_d_path_command_sequence(self):
        commands = motion_task_to_u1_commands(
            {
                "capability": "run_path",
                "task_id": "task-path",
                "params": {
                    "feed": 900,
                    "path": [
                        {"cmd": "M", "x": 0, "y": 0},
                        {"cmd": "L", "x": 10, "y": 0},
                    ],
                },
            }
        )

        self.assertEqual([command["cmd"] for command in commands], ["PATH_BEGIN", "PATH_SEG", "PATH_SEG", "PATH_END"])
        self.assertEqual(commands[0]["total_segments"], 2)
        self.assertEqual(commands[1]["segment_index"], 0)
        self.assertEqual(commands[1]["segment_cmd"], "M")
        self.assertEqual(commands[2]["segment_index"], 1)
        self.assertEqual(commands[2]["feed"], 900)

    def test_device_info_report_from_u1_response(self):
        report = device_info_report_from_u1_response(
            {"device_id": "dev-1", "task_id": "task-info"},
            {
                "type": "result",
                "model": "DLC Motor Control P1 XYYZ",
                "hw_rev": "DLC_Motor_Control_P1_V1.0_260513",
                "fw_rev": "fake-u1",
                "workspace_mm": {"x": 200.0, "y": 150.0, "z": 50.0},
            },
        )
        self.assertEqual(
            report,
            {
                "device_id": "dev-1",
                "task_id": "task-info",
                "model": "DLC Motor Control P1 XYYZ",
                "hw_rev": "DLC_Motor_Control_P1_V1.0_260513",
                "fw_rev": "fake-u1",
                "workspace_mm": {"x": 200.0, "y": 150.0, "z": 50.0},
            },
        )


if __name__ == "__main__":
    unittest.main()


class TestM5M6Routes(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        FakeDeviceServerHandler.business_base_url = ""
        FakeDeviceServerHandler.internal_token = "test-token"
        cls.server = HTTPServer(("127.0.0.1", 0), FakeDeviceServerHandler)
        cls.host, cls.port = cls.server.server_address
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=5)

    def _post(self, path, body, token="test-token"):
        req = urllib.request.Request(
            f"http://{self.host}:{self.port}{path}",
            data=json.dumps(body).encode("utf-8"),
            headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=5) as resp:
            return json.loads(resp.read())

    def _get(self, path, token="test-token"):
        req = urllib.request.Request(
            f"http://{self.host}:{self.port}{path}",
            headers={"Authorization": f"Bearer {token}"},
        )
        with urllib.request.urlopen(req, timeout=5) as resp:
            return json.loads(resp.read())

    def test_self_check_accepts_valid_payload(self):
        result = self._post("/internal/v1/self_check", {"device_id": "dev-1", "status": "passed"})
        self.assertEqual(result["code"], 0)

    def test_voiceprint_cache_get_returns_empty(self):
        result = self._get("/internal/v1/voiceprints/cache?device_id=dev-1")
        self.assertEqual(result["code"], 0)
        self.assertEqual(result["data"]["voiceprints"], [])
        self.assertEqual(result["data"]["mode"], "voiceprint_off")

    def test_firmware_plan_returns_no_update(self):
        result = self._post("/internal/v1/firmware/plan", {"device_id": "dev-1", "current_version": "1.0.0"})
        self.assertEqual(result["code"], 0)
        self.assertFalse(result["data"]["has_update"])

    def test_firmware_install_result_accepts(self):
        result = self._post("/internal/v1/firmware/install_result", {"device_id": "dev-1", "success": True})
        self.assertEqual(result["code"], 0)

    def test_unauthorized_request_rejected(self):
        req = urllib.request.Request(
            f"http://{self.host}:{self.port}/internal/v1/self_check",
            data=json.dumps({"device_id": "dev-1"}).encode("utf-8"),
            headers={"Content-Type": "application/json", "Authorization": "Bearer wrong-token"},
            method="POST",
        )
        try:
            urllib.request.urlopen(req, timeout=5)
            self.fail("Expected HTTP error")
        except urllib.error.HTTPError as e:
            self.assertEqual(e.code, 401)
