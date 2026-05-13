import json
import unittest

from tools.fake_u1.app import FakeU1Simulator


class FakeU1SimulatorTests(unittest.TestCase):
    def setUp(self) -> None:
        self.sim = FakeU1Simulator()

    def send(self, payload):
        raw = self.sim.handle_line("@" + json.dumps(payload))
        return json.loads(raw)

    def test_get_status_happy_path(self):
        data = self.send({"msg_id": "1", "task_id": "t_status", "cmd": "GET_STATUS"})
        self.assertEqual(data["type"], "status")
        self.assertEqual(data["msg_id"], "1")
        self.assertEqual(data["task_id"], "t_status")
        self.assertFalse(data["homed"])

    def test_home_then_move_happy_path(self):
        home = self.send({"msg_id": "2", "task_id": "t_home", "cmd": "HOME"})
        self.assertEqual(home["type"], "result")
        self.assertEqual(home["result"], "DONE")

        move = self.send({"msg_id": "3", "task_id": "t_move", "cmd": "MOVE", "x": 10, "y": 20, "z": 3, "feed": 1200})
        self.assertEqual(move["type"], "result")
        self.assertEqual(move["result"], "DONE")

        status = self.send({"msg_id": "4", "task_id": "t_status2", "cmd": "GET_STATUS"})
        self.assertTrue(status["homed"])
        self.assertEqual(status["position"], {"x": 10.0, "y": 20.0, "z": 3.0})

    def test_move_requires_homing_e001(self):
        move = self.send({"msg_id": "5", "task_id": "t_move_err", "cmd": "MOVE", "x": 1})
        self.assertEqual(move["type"], "error")
        self.assertEqual(move["error_code"], "E001")

    def test_injected_limit_e005(self):
        self.sim.queue_injection("E005")
        home = self.send({"msg_id": "6", "task_id": "t_home_limit", "cmd": "HOME"})
        self.assertEqual(home["type"], "error")
        self.assertEqual(home["error_code"], "E005")
        self.assertEqual(home["state"], "ALARM")
        self.assertEqual(home["alarm_code"], "E005")

    def test_injected_estop_e008(self):
        self.sim.queue_injection("E008")
        home = self.send({"msg_id": "7", "task_id": "t_home_estop", "cmd": "HOME"})
        self.assertEqual(home["type"], "error")
        self.assertEqual(home["error_code"], "E008")
        self.assertEqual(home["state"], "ESTOP")

        status = self.send({"msg_id": "8", "task_id": "t_status3", "cmd": "GET_STATUS"})
        self.assertEqual(status["state"], "ESTOP")
        self.assertFalse(status["homed"])


if __name__ == "__main__":
    unittest.main()
