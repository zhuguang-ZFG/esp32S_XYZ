"""Route policy validation for fake U1 firmware reference implementation.

Aligns with LiMa ``device_gateway/path_validator.py`` and Edge-C schema.
The fake U1 rejects unknown route_role / primary_strategy / artifact_required /
backend combinations before executing any motion.
"""

from __future__ import annotations

from typing import Any

VALID_ROUTE_ROLES = frozenset(
    {"device_control", "device_write", "device_draw", "device_vector", "device_unknown"}
)
VALID_PRIMARY_STRATEGIES = frozenset(
    {"deterministic", "image_then_vector", "svg_vector", "provided_path", "planner_required"}
)
VALID_ARTIFACT_REQUIRED = frozenset({"none", "preview_svg", "vector_path"})
VALID_BACKENDS = frozenset(
    {"deterministic", "dashscope_wanx", "dashscope_flux", "opencv_contour", ""}
)

# role -> allowed primary_strategies
_ROLE_STRATEGIES: dict[str, frozenset[str]] = {
    "device_control": frozenset({"deterministic"}),
    "device_write": frozenset({"deterministic", "provided_path"}),
    "device_draw": frozenset({"image_then_vector", "svg_vector"}),
    "device_vector": frozenset({"provided_path", "svg_vector"}),
    "device_unknown": frozenset({"planner_required"}),
}

# role -> allowed artifact_required
_ROLE_ARTIFACTS: dict[str, frozenset[str]] = {
    "device_control": frozenset({"none"}),
    "device_write": frozenset({"preview_svg", "vector_path"}),
    "device_draw": frozenset({"preview_svg", "vector_path"}),
    "device_vector": frozenset({"preview_svg", "vector_path"}),
    "device_unknown": frozenset({"none", "preview_svg", "vector_path"}),
}


def validate_route_policy_for_u1(
    route_policy: dict[str, Any] | None,
    fw_capabilities: set[str] | None = None,
) -> tuple[bool, str, str]:
    """Validate a route_policy for the fake U1.

    Returns (ok, error_code, message).  error_code is a MotionErrorCode string
    compatible with the Edge-D error schema (E009 = bad params, E_UNSUPPORTED
    variants are not used here to keep the simulator simple).
    """
    if not isinstance(route_policy, dict):
        return False, "E009", "missing route_policy"

    route_role = str(route_policy.get("route_role", ""))
    primary_strategy = str(route_policy.get("primary_strategy", ""))
    artifact_required = str(route_policy.get("artifact_required", ""))
    model_required = route_policy.get("model_required")
    backend = str(route_policy.get("backend", ""))

    if route_role not in VALID_ROUTE_ROLES:
        return False, "E009", f"unknown route_role: {route_role}"

    if primary_strategy not in VALID_PRIMARY_STRATEGIES:
        return False, "E009", f"unknown primary_strategy: {primary_strategy}"

    if artifact_required not in VALID_ARTIFACT_REQUIRED:
        return False, "E009", f"unknown artifact_required: {artifact_required}"

    if backend and backend not in VALID_BACKENDS:
        return False, "E009", f"unknown backend: {backend}"

    if primary_strategy not in _ROLE_STRATEGIES.get(route_role, frozenset()):
        return False, "E009", f"strategy {primary_strategy} not allowed for {route_role}"

    if artifact_required not in _ROLE_ARTIFACTS.get(route_role, frozenset()):
        return False, "E009", f"artifact {artifact_required} not allowed for {route_role}"

    if route_role == "device_control" and model_required is True:
        return False, "E009", "device_control cannot require a model"

    if route_role in {"device_draw", "device_vector"}:
        caps = fw_capabilities or set()
        if "run_path" not in caps:
            return False, "E009", f"{route_role} requires run_path capability"

    return True, "", ""
