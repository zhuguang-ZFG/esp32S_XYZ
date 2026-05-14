#!/usr/bin/env python3
"""
M0c.2: fake DeviceServer — 最小 HTTP server，模拟 xiaozhi-server 的
motion_task 接收 + 转发到 fake_u1 + motion_event 上行。

不依赖真实 xiaozhi-server、不依赖真实 U8。
"""
import argparse
import json
import socket
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FAKE_U1_HOST = "127.0.0.1"
FAKE_U1_PORT = 7799


def send_to_fake_u1(line: str, host: str = FAKE_U1_HOST, port: int = FAKE_U1_PORT) -> dict | None:
    """向 fake_u1 发 @json 协议帧，返回解析后的响应 dict。"""
    try:
        s = socket.create_connection((host, port), timeout=5)
        # 如果缺少 @ 前缀则补上
        frame = line if line.startswith("@") else "@" + line
        s.sendall((frame + "\n").encode())
        resp = s.recv(8192).decode("utf-8", errors="replace")
        s.close()
        for segment in resp.strip().split("\n"):
            segment = segment.strip()
            if segment.startswith("@"):
                segment = segment[1:]
            if segment:
                return json.loads(segment)
    except Exception as exc:
        return {"type": "error", "error_code": "E_FAKE_U1", "message": str(exc)}
    return None


class FakeDeviceServerHandler(BaseHTTPRequestHandler):
    """
    POST /internal/v1/motion_task  → 下发给 fake_u1 并返回 ack
    POST /internal/v1/motion_event → 上行转发给 BusinessServer（记录日志）
    POST /internal/v1/device_info  → 设备信息上报
    """
    business_base_url: str = ""
    internal_token: str = ""

    def log_message(self, fmt, *args):
        print(f"[fake_device_server] {args[0] if args else fmt}")

    def _json_body(self):
        length = int(self.headers.get("Content-Length", "0"))
        if not length:
            return None
        return json.loads(self.rfile.read(length))

    def _send_json(self, status: int, body: dict):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(body).encode())

    def do_POST(self):
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
        auth = self.headers.get("Authorization", "")
        expected = f"Bearer {self.internal_token}"
        if not self.internal_token:
            return True  # dev mode, skip auth
        return auth == expected

    def _handle_motion_task(self):
        if not self._check_auth():
            self._send_json(401, {"code": 401, "message": "unauthorized"})
            return
        body = self._json_body()
        if not body:
            self._send_json(400, {"code": 400, "message": "empty body"})
            return

        capability = body.get("capability", "")
        task_id = body.get("task_id", "unknown")
        device_id = body.get("device_id", "")

        # 构造发给 fake_u1 的命令
        cmd_map = {
            "home": '{"msg_id":"1","task_id":"%s","cmd":"HOME"}' % task_id,
            "get_status": '{"msg_id":"1","task_id":"%s","cmd":"GET_STATUS"}',
            "move_abs": '{"msg_id":"1","task_id":"%s","cmd":"MOVE","x":%s,"y":%s,"z":%s,"feed":%s}' % (
                task_id,
                body.get("params", {}).get("x", 0),
                body.get("params", {}).get("y", 0),
                body.get("params", {}).get("z", 0),
                body.get("params", {}).get("feed", 1200),
            ),
        }
        cmd_json = cmd_map.get(capability, '{"msg_id":"1","task_id":"%s","cmd":"%s"}' % (task_id, capability.upper()))

        result = send_to_fake_u1(cmd_json)
        print(f"  motion_task {capability} task_id={task_id} → fake_u1: {result.get('type','?') if result else 'no-response'}")

        self._send_json(200, {
            "code": 0,
            "message": "task forwarded",
            "data": {"task_id": task_id, "status": result.get("state", "unknown") if result else "dispatched"},
        })

    def _handle_motion_event(self):
        if not self._check_auth():
            self._send_json(401, {"code": 401, "message": "unauthorized"})
            return
        body = self._json_body()
        if not body:
            self._send_json(400, {"code": 400, "message": "empty body"})
            return
        print(f"  motion_event ingest: task_id={body.get('task_id')} phase={body.get('phase')} device_id={body.get('device_id')}")
        # M2.6: 只打日志，转发到 BusinessServer 是可选的后续扩展
        if self.business_base_url:
            self._forward_to_business("/internal/v1/motion_event", body)
        self._send_json(200, {"code": 0})

    def _handle_device_info(self):
        if not self._check_auth():
            self._send_json(401, {"code": 401, "message": "unauthorized"})
            return
        body = self._json_body()
        if not body:
            self._send_json(400, {"code": 400, "message": "empty body"})
            return
        print(f"  device_info report: device_id={body.get('device_id')} model={body.get('model')}")
        self._send_json(200, {"code": 0})

    def _forward_to_business(self, path: str, body: dict):
        """转发到 BusinessServer（未来扩展）。"""
        try:
            import urllib.request
            req = urllib.request.Request(
                f"{self.business_base_url}{path}",
                data=json.dumps(body).encode(),
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {self.internal_token}",
                },
                method="POST",
            )
            urllib.request.urlopen(req, timeout=5)
        except Exception as e:
            print(f"  forward to business failed: {e}")


def build_arg_parser():
    p = argparse.ArgumentParser(description="fake DeviceServer — M0c.2")
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", default=8100, type=int)
    p.add_argument("--fake-u1-host", default=FAKE_U1_HOST)
    p.add_argument("--fake-u1-port", default=FAKE_U1_PORT, type=int)
    p.add_argument("--business-base-url", default="", help="BusinessServer URL for event forwarding")
    p.add_argument("--internal-token", default="", help="shared secret")
    return p


def main():
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
