import json
import unittest
from pathlib import Path

from jsonschema import Draft202012Validator
from tools.fake_u1.app import FakeU1Simulator


ROOT = Path(__file__).resolve().parents[3]
EDGE_D = ROOT / "docs" / "schemas" / "edge_d"


def load_schema(name: str) -> dict:
    return json.loads((EDGE_D / name).read_text(encoding="utf-8-sig"))


class FakeU1SimulatorTests(unittest.TestCase):
    def setUp(self) -> None:
        self.sim = FakeU1Simulator()
        self.ack_schema = Draft202012Validator(load_schema("ack.schema.json"))
        self.device_info_schema = Draft202012Validator(load_schema("device_info.schema.json"))
        self.status_schema = Draft202012Validator(load_schema("status.schema.json"))
        self.result_schema = Draft202012Validator(load_schema("result.schema.json"))
        self.error_schema = Draft202012Validator(load_schema("error.schema.json"))

    def send(self, payload):
        raw = self.sim.handle_line("@" + json.dumps(payload))
        return json.loads(raw)

    def assert_schema_valid(self, schema: Draft202012Validator, payload: dict) -> None:
        errors = sorted(schema.iter_errors(payload), key=lambda error: list(error.path))
        self.assertEqual(errors, [], [error.message for error in errors])

    def test_get_status_happy_path(self):
        data = self.send({"msg_id": "1", "task_id": "t_status", "cmd": "GET_STATUS"})
        self.assertEqual(data["type"], "status")
        self.assertEqual(data["msg_id"], "1")
        self.assertEqual(data["task_id"], "t_status")
        self.assertFalse(data["homed"])
        self.assert_schema_valid(self.status_schema, data)

    def test_home_then_move_happy_path(self):
        home = self.send({"msg_id": "2", "task_id": "t_home", "cmd": "HOME"})
        self.assertEqual(home["type"], "result")
        self.assertEqual(home["result"], "DONE")
        self.assert_schema_valid(self.result_schema, home)

        move = self.send({"msg_id": "3", "task_id": "t_move", "cmd": "MOVE", "x": 10, "y": 20, "z": 3, "feed": 1200})
        self.assertEqual(move["type"], "result")
        self.assertEqual(move["result"], "DONE")
        self.assert_schema_valid(self.result_schema, move)

        status = self.send({"msg_id": "4", "task_id": "t_status2", "cmd": "GET_STATUS"})
        self.assertTrue(status["homed"])
        self.assertEqual(status["position"], {"x": 10.0, "y": 20.0, "z": 3.0})
        self.assert_schema_valid(self.status_schema, status)

    def test_move_requires_homing_e001(self):
        move = self.send({"msg_id": "5", "task_id": "t_move_err", "cmd": "MOVE", "x": 1})
        self.assertEqual(move["type"], "error")
        self.assertEqual(move["error_code"], "E001")
        self.assert_schema_valid(self.error_schema, move)

    def test_move_soft_limit_e002(self):
        self.send({"msg_id": "5a", "task_id": "t_home", "cmd": "HOME"})
        move = self.send({"msg_id": "5b", "task_id": "t_move_limit", "cmd": "MOVE", "x": 201})
        self.assertEqual(move["type"], "error")
        self.assertEqual(move["error_code"], "E002")
        self.assert_schema_valid(self.error_schema, move)

    def test_injected_limit_e005(self):
        self.sim.queue_injection("E005")
        home = self.send({"msg_id": "6", "task_id": "t_home_limit", "cmd": "HOME"})
        self.assertEqual(home["type"], "error")
        self.assertEqual(home["error_code"], "E005")
        self.assertEqual(home["state"], "ALARM")
        self.assertEqual(home["alarm_code"], "E005")
        self.assert_schema_valid(self.error_schema, home)

    def test_injected_homing_failure_e006(self):
        self.sim.queue_injection("E006")
        home = self.send({"msg_id": "6a", "task_id": "t_home_fail", "cmd": "HOME"})
        self.assertEqual(home["type"], "error")
        self.assertEqual(home["error_code"], "E006")
        self.assertEqual(home["state"], "ALARM")
        self.assertEqual(home["alarm_code"], "E006")
        self.assert_schema_valid(self.error_schema, home)

    def test_injected_estop_e008(self):
        self.sim.queue_injection("E008")
        home = self.send({"msg_id": "7", "task_id": "t_home_estop", "cmd": "HOME"})
        self.assertEqual(home["type"], "error")
        self.assertEqual(home["error_code"], "E008")
        self.assertEqual(home["state"], "ESTOP")
        self.assert_schema_valid(self.error_schema, home)

        status = self.send({"msg_id": "8", "task_id": "t_status3", "cmd": "GET_STATUS"})
        self.assertEqual(status["state"], "ESTOP")
        self.assertFalse(status["homed"])
        self.assert_schema_valid(self.status_schema, status)

    def test_path_pause_resume_stop(self):
        self.send({"msg_id": "9", "task_id": "t_home", "cmd": "HOME"})
        begin = self.send({"msg_id": "10", "task_id": "t_path", "cmd": "PATH_BEGIN", "total_segments": 1})
        self.assertEqual(begin["type"], "ack")
        self.assertEqual(begin["state"], "RUNNING")
        self.assert_schema_valid(self.ack_schema, begin)

        segment = self.send(
            {
                "msg_id": "11",
                "task_id": "t_path",
                "cmd": "PATH_SEG",
                "segment_index": 0,
                "segment_cmd": "L",
                "x": 12,
                "y": 6,
                "z": 1,
                "feed": 1000,
            }
        )
        self.assertEqual(segment["type"], "ack")
        self.assert_schema_valid(self.ack_schema, segment)

        paused = self.send({"msg_id": "12", "task_id": "t_path", "cmd": "PAUSE"})
        self.assertEqual(paused["type"], "ack")
        self.assertEqual(paused["state"], "PAUSED")
        self.assert_schema_valid(self.ack_schema, paused)

        resumed = self.send({"msg_id": "13", "task_id": "t_path", "cmd": "RESUME"})
        self.assertEqual(resumed["type"], "ack")
        self.assertEqual(resumed["state"], "RUNNING")
        self.assert_schema_valid(self.ack_schema, resumed)

        stopped = self.send({"msg_id": "14", "task_id": "t_path", "cmd": "STOP"})
        self.assertEqual(stopped["type"], "result")
        self.assertEqual(stopped["result"], "CANCELLED")
        self.assert_schema_valid(self.result_schema, stopped)

    def test_path_updates_position_on_end(self):
        self.send({"msg_id": "15", "task_id": "t_home", "cmd": "HOME"})
        self.send({"msg_id": "16", "task_id": "t_path", "cmd": "PATH_BEGIN", "total_segments": 1})
        self.send({"msg_id": "17", "task_id": "t_path", "cmd": "PATH_SEG", "segment_index": 0, "segment_cmd": "L", "x": 20})
        done = self.send({"msg_id": "18", "task_id": "t_path", "cmd": "PATH_END"})
        self.assertEqual(done["type"], "result")
        self.assertEqual(done["result"], "DONE")
        self.assert_schema_valid(self.result_schema, done)

        status = self.send({"msg_id": "19", "task_id": "t_status4", "cmd": "GET_STATUS"})
        self.assertEqual(status["position"], {"x": 20.0, "y": 0.0, "z": 0.0})
        self.assert_schema_valid(self.status_schema, status)

    def test_get_device_info_is_schema_valid(self):
        data = self.send({"msg_id": "20", "cmd": "GET_DEVICE_INFO"})
        self.assertEqual(data["type"], "result")
        self.assertEqual(data["task_id"], "device-info")
        self.assertEqual(data["model"], "DLC Motor Control P1 XYYZ")
        self.assertEqual(data["workspace_mm"], {"x": 200.0, "y": 150.0, "z": 50.0})
        self.assert_schema_valid(self.device_info_schema, data)


if __name__ == "__main__":
    unittest.main()
