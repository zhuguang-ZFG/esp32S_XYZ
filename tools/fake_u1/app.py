#!/usr/bin/env python3
import argparse
import json
import socketserver
from collections import deque
from dataclasses import dataclass, field
from typing import Deque, Dict, List, Optional


INJECTABLE_ERRORS: Dict[str, Dict[str, Optional[str]]] = {
    "E001": {"state": "ERROR", "message": "home required", "alarm_code": None},
    "E005": {"state": "ALARM", "message": "limit triggered", "alarm_code": "E005"},
    "E008": {"state": "ESTOP", "message": "estop triggered", "alarm_code": "E008"},
}


@dataclass
class FakeU1State:
    homed: bool = False
    state: str = "IDLE"
    position: Dict[str, float] = field(default_factory=lambda: {"x": 0.0, "y": 0.0, "z": 0.0})
    active_task_id: str = ""
    alarm_code: Optional[str] = None
    path_total_segments: int = 0
    path_received_segments: int = 0
    path_task_id: str = ""


class FakeU1Simulator:
    def __init__(self, inject_codes: Optional[List[str]] = None) -> None:
        self.state = FakeU1State()
        self.inject_codes: Deque[str] = deque()
        if inject_codes:
            for code in inject_codes:
                self.queue_injection(code)

    def queue_injection(self, code: str) -> None:
        normalized = code.upper()
        if normalized not in INJECTABLE_ERRORS:
            raise ValueError(f"unsupported inject code: {code}")
        self.inject_codes.append(normalized)

    def handle_line(self, line: str) -> str:
        text = line.strip()
        if not text:
            return ""
        if text.startswith("@"):
            text = text[1:]

        try:
            payload = json.loads(text)
        except json.JSONDecodeError:
            return self._error("", "", "E009", "invalid json", state="ERROR")

        msg_id = str(payload.get("msg_id", ""))
        task_id = str(payload.get("task_id", ""))
        cmd = payload.get("cmd")
        if not isinstance(cmd, str) or not cmd:
            return self._error(msg_id, task_id, "E009", "missing cmd", state="ERROR")

        if cmd == "GET_STATUS":
            return self._status(msg_id, task_id)
        if cmd == "HOME":
            return self._home(msg_id, task_id)
        if cmd == "MOVE":
            return self._move(msg_id, task_id, payload)
        if cmd == "PATH_BEGIN":
            return self._path_begin(msg_id, task_id, payload)
        if cmd == "PATH_SEG":
            return self._path_seg(msg_id, task_id, payload)
        if cmd == "PATH_END":
            return self._path_end(msg_id, task_id)
        if cmd == "ESTOP":
            self.state.state = "ESTOP"
            self.state.homed = False
            self.state.active_task_id = ""
            self.state.alarm_code = "E008"
            return self._ack(msg_id, task_id, state="ESTOP")
        return self._error(msg_id, task_id, "E010", "unsupported cmd", state="ERROR")

    def _consume_injected_error(self, msg_id: str, task_id: str) -> Optional[str]:
        if not self.inject_codes:
            return None
        code = self.inject_codes.popleft()
        meta = INJECTABLE_ERRORS[code]
        self.state.state = str(meta["state"])
        self.state.alarm_code = str(meta["alarm_code"]) if meta["alarm_code"] else None
        if code == "E008":
            self.state.homed = False
        return self._error(msg_id, task_id, code, str(meta["message"]), state=self.state.state, alarm_code=self.state.alarm_code)

    def _home(self, msg_id: str, task_id: str) -> str:
        injected = self._consume_injected_error(msg_id, task_id)
        if injected is not None:
            return injected
        self.state.homed = True
        self.state.state = "IDLE"
        self.state.position = {"x": 0.0, "y": 0.0, "z": 0.0}
        self.state.active_task_id = task_id
        self.state.alarm_code = None
        return self._result(msg_id, task_id, "DONE", state="IDLE")

    def _move(self, msg_id: str, task_id: str, payload: Dict[str, object]) -> str:
        has_axis = any(axis in payload for axis in ("x", "y", "z"))
        if not has_axis:
            return self._error(msg_id, task_id, "E009", "missing axis target", state="ERROR")
        if not self.state.homed:
            self.state.state = "ERROR"
            return self._error(msg_id, task_id, "E001", "home required", state="ERROR")
        injected = self._consume_injected_error(msg_id, task_id)
        if injected is not None:
            return injected
        for axis in ("x", "y", "z"):
            if axis in payload:
                self.state.position[axis] = float(payload[axis])
        self.state.state = "IDLE"
        self.state.active_task_id = task_id
        self.state.alarm_code = None
        return self._result(msg_id, task_id, "DONE", state="IDLE")

    def _path_begin(self, msg_id: str, task_id: str, payload: Dict[str, object]) -> str:
        total_segments = payload.get("total_segments")
        if not isinstance(total_segments, int) or total_segments <= 0:
            return self._error(msg_id, task_id, "E003", "invalid total_segments", state="ERROR")
        self.state.path_total_segments = total_segments
        self.state.path_received_segments = 0
        self.state.path_task_id = task_id
        return self._ack(msg_id, task_id, state="IDLE")

    def _path_seg(self, msg_id: str, task_id: str, payload: Dict[str, object]) -> str:
        if self.state.path_total_segments <= 0:
            return self._error(msg_id, task_id, "E003", "invalid path segment", state="ERROR")
        segment_index = payload.get("segment_index")
        segment_cmd = payload.get("segment_cmd")
        if not isinstance(segment_index, int) or segment_cmd not in ("M", "L"):
            return self._error(msg_id, task_id, "E003", "invalid path segment", state="ERROR")
        if segment_index != self.state.path_received_segments:
            return self._error(msg_id, task_id, "E009", "unexpected segment index", state="ERROR")
        self.state.path_received_segments += 1
        return self._ack(msg_id, task_id, state="IDLE")

    def _path_end(self, msg_id: str, task_id: str) -> str:
        if self.state.path_total_segments <= 0 or self.state.path_received_segments != self.state.path_total_segments:
            self._reset_path()
            return self._error(msg_id, task_id, "E009", "incomplete path", state="ERROR")
        injected = self._consume_injected_error(msg_id, task_id)
        self._reset_path()
        if injected is not None:
            return injected
        self.state.active_task_id = task_id
        self.state.state = "IDLE"
        self.state.alarm_code = None
        return self._result(msg_id, task_id, "DONE", state="IDLE")

    def _reset_path(self) -> None:
        self.state.path_total_segments = 0
        self.state.path_received_segments = 0
        self.state.path_task_id = ""

    def _status(self, msg_id: str, task_id: str) -> str:
        body = {
            "msg_id": msg_id,
            "type": "status",
            "task_id": task_id,
            "state": self.state.state,
            "homed": self.state.homed,
            "position": self.state.position,
            "active_task_id": self.state.active_task_id or None,
            "alarm_code": self.state.alarm_code,
            "error_code": None,
        }
        return json.dumps(body, separators=(",", ":"))

    @staticmethod
    def _ack(msg_id: str, task_id: str, state: str = "IDLE") -> str:
        return json.dumps(
            {"msg_id": msg_id, "type": "ack", "task_id": task_id, "state": state},
            separators=(",", ":"),
        )

    @staticmethod
    def _result(msg_id: str, task_id: str, result: str, state: str = "IDLE") -> str:
        return json.dumps(
            {"msg_id": msg_id, "type": "result", "task_id": task_id, "result": result, "state": state},
            separators=(",", ":"),
        )

    @staticmethod
    def _error(
        msg_id: str,
        task_id: str,
        error_code: str,
        message: str,
        *,
        state: str = "ERROR",
        alarm_code: Optional[str] = None,
    ) -> str:
        body = {
            "msg_id": msg_id,
            "type": "error",
            "task_id": task_id,
            "state": state,
            "error_code": error_code,
            "message": message,
        }
        if alarm_code is not None:
            body["alarm_code"] = alarm_code
        return json.dumps(body, separators=(",", ":"))


class FakeU1RequestHandler(socketserver.StreamRequestHandler):
    def handle(self) -> None:
        server: "FakeU1TCPServer" = self.server  # type: ignore[assignment]
        while True:
            line = self.rfile.readline()
            if not line:
                return
            response = server.simulator.handle_line(line.decode("utf-8", errors="replace"))
            if not response:
                continue
            self.wfile.write((response + "\n").encode("utf-8"))
            self.wfile.flush()


class FakeU1TCPServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True

    def __init__(self, server_address, simulator: FakeU1Simulator):
        self.simulator = simulator
        super().__init__(server_address, FakeU1RequestHandler)


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Minimal fake U1 private-protocol server.")
    parser.add_argument("--host", default="127.0.0.1", help="TCP listen host")
    parser.add_argument("--port", default=7799, type=int, help="TCP listen port")
    parser.add_argument(
        "--inject",
        action="append",
        default=[],
        metavar="CODE",
        help="Queue an injected error for the next command. Supported: E001, E005, E008",
    )
    return parser


def main() -> None:
    args = build_arg_parser().parse_args()
    simulator = FakeU1Simulator(inject_codes=args.inject)
    with FakeU1TCPServer((args.host, args.port), simulator) as server:
        print(f"fake_u1 listening on {args.host}:{args.port}")
        server.serve_forever()


if __name__ == "__main__":
    main()
