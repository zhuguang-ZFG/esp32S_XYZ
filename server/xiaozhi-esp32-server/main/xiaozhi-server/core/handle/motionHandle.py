"""M2.4：motion_task 下行至设备 WebSocket 的负载构造与派发（仅 DeviceServer 侧）。"""

from __future__ import annotations

from typing import Any, Mapping

# Control capabilities that never need a model or path planning.
# 须与 device_gateway/model_routing.py:resolve_device_route_policy 语义保持同步。
CONTROL_CAPABILITIES = frozenset({
    "home", "pause", "resume", "stop", "estop", "get_device_info",
})


def generate_route_policy(capability: str) -> dict[str, Any]:
    """Generate route_policy for an Edge-C motion_task from its capability.

    Semantics MUST stay aligned with the cloud-side
    device_gateway/model_routing.py:resolve_device_route_policy so the
    Edge-C downlink frame's route_role is consistent whether the policy
    is resolved in the cloud or filled in here at the DeviceServer.
    """
    if capability in CONTROL_CAPABILITIES:
        return {
            "route_role": "device_control",
            "model_required": False,
            "primary_strategy": "deterministic",
            "artifact_required": "none",
        }
    if capability == "run_path":
        return {
            "route_role": "device_vector",
            "model_required": False,
            "primary_strategy": "provided_path",
            "artifact_required": "preview_svg",
        }
    return {
        "route_role": "device_unknown",
        "model_required": True,
        "primary_strategy": "planner_required",
        "artifact_required": "none",
    }


def build_motion_task_websocket_message(body: Mapping[str, Any]) -> dict[str, Any]:
    """与实施计划 v2 §M2.4 一致：WSS 帧 type=motion_task。

    Edge-C 硬契约：每个下行帧必带 route_policy。若 body 已含则透传
    （尊重上游决策），否则按 capability 生成。
    """
    params = body.get("params")
    constraints = body.get("constraints")
    route_policy = body.get("route_policy")
    if not isinstance(route_policy, dict):
        route_policy = generate_route_policy(str(body.get("capability", "")))
    return {
        "type": "motion_task",
        "task_id": body.get("task_id"),
        "device_id": body.get("device_id"),
        "account_id": body.get("account_id"),
        "capability": body.get("capability"),
        "source": body.get("source"),
        "route_policy": route_policy,
        "request_id": body.get("request_id"),
        "trace_id": body.get("trace_id"),
        "params": params if isinstance(params, dict) else {},
        "constraints": constraints if isinstance(constraints, dict) else {},
    }
