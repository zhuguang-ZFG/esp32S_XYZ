"""M2.4：motion_task 下行至设备 WebSocket 的负载构造与派发（仅 DeviceServer 侧）。"""

from __future__ import annotations

from typing import Any, Mapping


def build_motion_task_websocket_message(body: Mapping[str, Any]) -> dict[str, Any]:
    """与实施计划 v2 §M2.4 一致：WSS 帧 type=motion_task。"""
    params = body.get("params")
    constraints = body.get("constraints")
    return {
        "type": "motion_task",
        "task_id": body.get("task_id"),
        "device_id": body.get("device_id"),
        "account_id": body.get("account_id"),
        "capability": body.get("capability"),
        "source": body.get("source"),
        "request_id": body.get("request_id"),
        "trace_id": body.get("trace_id"),
        "params": params if isinstance(params, dict) else {},
        "constraints": constraints if isinstance(constraints, dict) else {},
    }
