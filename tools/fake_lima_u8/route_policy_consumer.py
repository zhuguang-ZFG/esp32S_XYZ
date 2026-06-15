"""Parse, validate, and record route_policy from downlink motion_task frames."""

from __future__ import annotations

import json
import os
import time
from typing import Any

_REQUIRED_KEYS = ("route_role", "model_required", "primary_strategy", "artifact_required")


def parse_route_policy(motion_task: dict[str, Any]) -> dict[str, Any]:
    """Require a valid route_policy object on every motion_task."""
    policy = motion_task.get("route_policy")
    if not isinstance(policy, dict):
        raise RuntimeError(f"motion_task missing route_policy: {motion_task}")
    missing = [key for key in _REQUIRED_KEYS if key not in policy]
    if missing:
        raise RuntimeError(f"route_policy missing keys {missing}: {policy}")
    return policy


def build_route_policy_evidence(route_policy: dict[str, Any]) -> dict[str, Any]:
    evidence: dict[str, Any] = {
        "consumed": True,
        "route_role": route_policy.get("route_role"),
        "model_required": route_policy.get("model_required"),
        "primary_strategy": route_policy.get("primary_strategy"),
        "artifact_required": route_policy.get("artifact_required"),
    }
    backend = route_policy.get("backend")
    if backend:
        evidence["backend"] = backend
    return evidence


def attach_route_policy_evidence(event: dict[str, Any], route_policy: dict[str, Any]) -> dict[str, Any]:
    enriched = dict(event)
    enriched["route_policy_evidence"] = build_route_policy_evidence(route_policy)
    return enriched


def record_route_policy_consumed(
    *,
    device_id: str,
    task_id: str,
    route_policy: dict[str, Any],
    artifact_dir: str = "device_artifacts",
    scenario: str = "success",
) -> dict[str, Any]:
    os.makedirs(artifact_dir, exist_ok=True)
    log_file = os.path.join(artifact_dir, f"fake_u8_route_policy_{device_id}.log")
    log_entry = {
        "timestamp": time.time(),
        "device_id": device_id,
        "task_id": task_id,
        "route_policy": route_policy,
        "consumed_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "scenario": scenario,
    }
    with open(log_file, "a", encoding="utf-8") as handle:
        handle.write(json.dumps(log_entry, ensure_ascii=False) + "\n")
    return {"type": "route_policy_consumed", "route_policy": route_policy, "scenario": scenario}
