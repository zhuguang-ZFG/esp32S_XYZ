"""Tests for route_policy generation in motionHandle.build_motion_task_websocket_message.

Guards the Edge-C hard contract: every downlink motion_task must carry
a valid route_policy. Semantics must stay aligned with the cloud-side
device_gateway/model_routing.py:resolve_device_route_policy.
"""
import importlib.util
import unittest
from pathlib import Path

# motionHandle.py lives under a non-package path (hyphenated dirs, no
# __init__.py), so load it by file path.
_REPO_ROOT = Path(__file__).resolve().parents[3]
_MODULE_PATH = (
    _REPO_ROOT / "server" / "xiaozhi-esp32-server" / "main"
    / "xiaozhi-server" / "core" / "handle" / "motionHandle.py"
)

if not _MODULE_PATH.exists():
    raise unittest.SkipTest(f"motionHandle.py not found at {_MODULE_PATH}; upstream module removed")

_spec = importlib.util.spec_from_file_location("motionHandle", _MODULE_PATH)
motionHandle = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(motionHandle)

build_motion_task_websocket_message = motionHandle.build_motion_task_websocket_message
generate_route_policy = motionHandle.generate_route_policy

_VALID_ROLES = {
    "device_control", "device_write", "device_draw",
    "device_vector", "device_unknown",
}
_VALID_STRATEGIES = {
    "deterministic", "image_then_vector", "svg_vector",
    "provided_path", "planner_required",
}
_VALID_ARTIFACTS = {"none", "preview_svg", "vector_path"}


def _assert_valid_route_policy(testcase, policy):
    testcase.assertIsInstance(policy, dict)
    testcase.assertIn(policy["route_role"], _VALID_ROLES)
    testcase.assertIsInstance(policy["model_required"], bool)
    testcase.assertIn(policy["primary_strategy"], _VALID_STRATEGIES)
    testcase.assertIn(policy["artifact_required"], _VALID_ARTIFACTS)


class TestGenerateRoutePolicy(unittest.TestCase):
    def test_control_capability_is_device_control(self):
        for cap in ("home", "pause", "resume", "stop", "estop", "get_device_info"):
            policy = generate_route_policy(cap)
            self.assertEqual(policy["route_role"], "device_control")
            self.assertFalse(policy["model_required"])
            self.assertEqual(policy["primary_strategy"], "deterministic")
            self.assertEqual(policy["artifact_required"], "none")

    def test_run_path_is_device_vector(self):
        policy = generate_route_policy("run_path")
        self.assertEqual(policy["route_role"], "device_vector")
        self.assertEqual(policy["primary_strategy"], "provided_path")

    def test_unknown_capability_is_device_unknown(self):
        policy = generate_route_policy("nonsense_cap")
        self.assertEqual(policy["route_role"], "device_unknown")
        self.assertTrue(policy["model_required"])
        self.assertEqual(policy["primary_strategy"], "planner_required")


class TestBuildMotionTaskRoutePolicy(unittest.TestCase):
    def _base_body(self, capability):
        return {
            "task_id": "task-1",
            "device_id": "dev-1",
            "capability": capability,
        }

    def test_route_policy_always_present_for_home(self):
        msg = build_motion_task_websocket_message(self._base_body("home"))
        self.assertEqual(msg["type"], "motion_task")
        _assert_valid_route_policy(self, msg["route_policy"])
        self.assertEqual(msg["route_policy"]["route_role"], "device_control")

    def test_route_policy_always_present_for_run_path(self):
        msg = build_motion_task_websocket_message(self._base_body("run_path"))
        _assert_valid_route_policy(self, msg["route_policy"])
        self.assertEqual(msg["route_policy"]["route_role"], "device_vector")

    def test_route_policy_always_present_for_unknown(self):
        msg = build_motion_task_websocket_message(self._base_body("weird"))
        _assert_valid_route_policy(self, msg["route_policy"])
        self.assertEqual(msg["route_policy"]["route_role"], "device_unknown")

    def test_route_policy_passthrough_not_overwritten(self):
        body = self._base_body("run_path")
        body["route_policy"] = {
            "route_role": "device_draw",
            "model_required": True,
            "primary_strategy": "image_then_vector",
            "artifact_required": "vector_path",
        }
        msg = build_motion_task_websocket_message(body)
        self.assertEqual(msg["route_policy"]["route_role"], "device_draw",
                         "upstream route_policy must be passed through, not regenerated")


if __name__ == "__main__":
    unittest.main()
