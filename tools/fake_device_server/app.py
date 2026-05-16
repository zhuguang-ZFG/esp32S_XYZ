#!/usr/bin/env python3
"""Minimal fake DeviceServer for M0c integration work."""

from __future__ import annotations

import argparse
import json
import socket
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Any, Dict, Optional


FAKE_U1_HOST = "127.0.0.1"
FAKE_U1_PORT = 7799


def motion_task_to_u1_command(body: Dict[str, Any]) -> Dict[str, Any]:
    capability = str(body.get("capability", ""))
    task_id = str(body.get("task_id", "unknown"))
    params = body.get("params", {})
    if not isinstance(params, dict):
        params = {}

    simple_map = {
        "home": "HOME",
        "get_status": "GET_STATUS",
        "get_device_info": "GET_DEVICE_INFO",
        "pause": "PAUSE",
        "resume": "RESUME",
        "stop": "STOP",
        "estop": "ESTOP",
    }
    if capability in simple_map:
        return {"msg_id": "1", "task_id": task_id, "cmd": simple_map[capability]}

    if capability == "move_abs":
        return {
            "msg_id": "1",
            "task_id": task_id,
            "cmd": "MOVE",
            "x": params.get("x", 0),
            "y": params.get("y", 0),
            "z": params.get("z", 0),
            "feed": params.get("feed", 1200),
        }

    if capability == "move_rel":
        return {
            "msg_id": "1",
            "task_id": task_id,
            "cmd": "MOVE_REL",
            "dx": params.get("dx", 0),
            "dy": params.get("dy", 0),
            "dz": params.get("dz", 0),
            "feed": params.get("feed", 800),
        }

    return {"msg_id": "1", "task_id": task_id, "cmd": capability.upper()}


def motion_task_to_u1_commands(body: Dict[str, Any]) -> list[Dict[str, Any]]:
    capability = str(body.get("capability", ""))
    task_id = str(body.get("task_id", "unknown"))
    params = body.get("params", {})
    if not isinstance(params, dict):
        params = {}

    if capability != "run_path":
        return [motion_task_to_u1_command(body)]

    path = params.get("path", [])
    if not isinstance(path, list):
        path = []
    feed = params.get("feed", 1200)
    commands: list[Dict[str, Any]] = [
        {"msg_id": "1", "task_id": task_id, "cmd": "PATH_BEGIN", "total_segments": len(path), "feed": feed}
    ]
    for index, segment in enumerate(path):
        if not isinstance(segment, dict):
            segment = {}
        commands.append(
            {
                "msg_id": str(index + 2),
                "task_id": task_id,
                "cmd": "PATH_SEG",
                "segment_index": index,
                "segment_cmd": segment.get("cmd", "L"),
                "x": segment.get("x", 0),
                "y": segment.get("y", 0),
                "z": segment.get("z", 0),
                "feed": segment.get("feed", feed),
            }
        )
    commands.append({"msg_id": str(len(path) + 2), "task_id": task_id, "cmd": "PATH_END"})
    return commands


def device_info_report_from_u1_response(body: Dict[str, Any], response: Dict[str, Any]) -> Dict[str, Any]:
    workspace = response.get("workspace_mm", {})
    if not isinstance(workspace, dict):
        workspace = {}
    return {
        "device_id": str(body.get("device_id", "")),
        "task_id": str(body.get("task_id", "")),
        "model": str(response.get("model", "")),
        "hw_rev": str(response.get("hw_rev", "")),
        "fw_rev": str(response.get("fw_rev", "")),
        "workspace_mm": {
            "x": workspace.get("x"),
            "y": workspace.get("y"),
            "z": workspace.get("z"),
        },
    }


def send_to_fake_u1(line: str, host: Optional[str] = None, port: Optional[int] = None) -> Optional[dict]:
    """Send one @{json} frame to fake_u1 and return the first JSON response."""
    target_host = host or FAKE_U1_HOST
    target_port = port if port is not None else FAKE_U1_PORT
    try:
        with socket.create_connection((target_host, target_port), timeout=5) as sock:
            frame = line if line.startswith("@") else "@" + line
            sock.sendall((frame + "\n").encode("utf-8"))
            response = sock.recv(8192).decode("utf-8", errors="replace")
    except Exception as exc:
        return {"type": "error", "error_code": "E_FAKE_U1", "message": str(exc)}

    for segment in response.strip().split("\n"):
        text = segment.strip()
        if text.startswith("@"):
            text = text[1:]
        if text:
            return json.loads(text)
    return None


class FakeDeviceServerHandler(BaseHTTPRequestHandler):
    """HTTP endpoints used by BusinessServer-side fake integration tests."""

    business_base_url: str = ""
    internal_token: str = ""

    def log_message(self, fmt: str, *args: object) -> None:
        print(f"[fake_device_server] {fmt % args if args else fmt}")

    def _json_body(self) -> Optional[dict]:
        length = int(self.headers.get("Content-Length", "0"))
        if not length:
            return None
        return json.loads(self.rfile.read(length))

    def _send_json(self, status: int, body: dict) -> None:
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(body, separators=(",", ":")).encode("utf-8"))

    def do_POST(self) -> None:
        path = self.path.rstrip("/")
        if path == "/internal/v1/motion_task":
            self._handle_motion_task()
        elif path == "/internal/v1/motion_event":
            self._handle_motion_event()
        elif path == "/internal/v1/device_info":
            self._handle_device_info()
        else:
            self._send_json(404, {"code": 404, "message": "not found"})

    def _check_auth(self) -> bool:
        if not self.internal_token:
            return True
        return self.headers.get("Authorization", "") == f"Bearer {self.internal_token}"

    def _handle_motion_task(self) -> None:
        if not self._check_auth():
            self._send_json(401, {"code": 401, "message": "unauthorized"})
            return

        body = self._json_body()
        if not body:
            self._send_json(400, {"code": 400, "message": "empty body"})
            return

        task_id = str(body.get("task_id", "unknown"))
        capability = str(body.get("capability", ""))
        commands = motion_task_to_u1_commands(body)
        result = None
        for command in commands:
            cmd_json = json.dumps(command, separators=(",", ":"))
            result = send_to_fake_u1(cmd_json)
            if result and result.get("type") == "error":
                break

        result_type = result.get("type", "?") if result else "no-response"
        print(f"  motion_task {capability} task_id={task_id} -> fake_u1: {result_type}")

        data: Dict[str, Any] = {"task_id": task_id, "status": result.get("state", "unknown") if result else "dispatched"}
        if capability == "get_device_info" and result:
            device_info = device_info_report_from_u1_response(body, result)
            data["device_info"] = device_info
            if self.business_base_url:
                self._forward_to_business("/internal/v1/device_info", device_info)

        self._send_json(
            200,
            {
                "code": 0,
                "message": "task forwarded",
                "data": data,
            },
        )

    def _handle_motion_event(self) -> None:
        if not self._check_auth():
            self._send_json(401, {"code": 401, "message": "unauthorized"})
            return

        body = self._json_body()
        if not body:
            self._send_json(400, {"code": 400, "message": "empty body"})
            return

        print(
            "  motion_event ingest: "
            f"task_id={body.get('task_id')} phase={body.get('phase')} device_id={body.get('device_id')}"
        )
        if self.business_base_url:
            self._forward_to_business("/internal/v1/motion_event", body)
        self._send_json(200, {"code": 0})

    def _handle_device_info(self) -> None:
        if not self._check_auth():
            self._send_json(401, {"code": 401, "message": "unauthorized"})
            return

        body = self._json_body()
        if not body:
            self._send_json(400, {"code": 400, "message": "empty body"})
            return

        print(f"  device_info report: device_id={body.get('device_id')} model={body.get('model')}")
        self._send_json(200, {"code": 0})

    def _forward_to_business(self, path: str, body: dict) -> None:
        try:
            import urllib.request

            req = urllib.request.Request(
                f"{self.business_base_url}{path}",
                data=json.dumps(body).encode("utf-8"),
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {self.internal_token}",
                },
                method="POST",
            )
            urllib.request.urlopen(req, timeout=5)
        except Exception as exc:
            print(f"  forward to business failed: {exc}")


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="fake DeviceServer - M0c.2")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default=8100, type=int)
    parser.add_argument("--fake-u1-host", default=FAKE_U1_HOST)
    parser.add_argument("--fake-u1-port", default=FAKE_U1_PORT, type=int)
    parser.add_argument("--business-base-url", default="", help="BusinessServer URL for event forwarding")
    parser.add_argument("--internal-token", default="", help="shared secret")
    return parser


def main() -> None:
    args = build_arg_parser().parse_args()

    global FAKE_U1_HOST, FAKE_U1_PORT
    FAKE_U1_HOST = args.fake_u1_host
    FAKE_U1_PORT = args.fake_u1_port

    handler = FakeDeviceServerHandler
    handler.business_base_url = args.business_base_url
    handler.internal_token = args.internal_token

    server = HTTPServer((args.host, args.port), handler)
    print(f"fake_device_server listening on http://{args.host}:{args.port}")
    print(f"  fake_u1 backend: {FAKE_U1_HOST}:{FAKE_U1_PORT}")
    if args.business_base_url:
        print(f"  business upstream: {args.business_base_url}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nshutdown")
        server.shutdown()


if __name__ == "__main__":
    main()
