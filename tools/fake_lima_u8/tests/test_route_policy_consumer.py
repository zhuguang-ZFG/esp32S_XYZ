import unittest

from fake_lima_u8.route_policy_consumer import (
    attach_route_policy_evidence,
    build_route_policy_evidence,
    parse_route_policy,
)


class TestRoutePolicyConsumer(unittest.TestCase):
    def test_parse_route_policy_requires_object(self):
        with self.assertRaises(RuntimeError):
            parse_route_policy({"task_id": "t1"})

    def test_build_route_policy_evidence_includes_backend(self):
        policy = {
            "route_role": "device_draw",
            "model_required": True,
            "primary_strategy": "image_then_vector",
            "artifact_required": "vector_path",
            "backend": "dashscope_wanx",
        }
        evidence = build_route_policy_evidence(policy)
        self.assertTrue(evidence["consumed"])
        self.assertEqual(evidence["backend"], "dashscope_wanx")

    def test_attach_route_policy_evidence_on_done_event(self):
        event = {"type": "motion_event", "phase": "done", "task_id": "t1"}
        enriched = attach_route_policy_evidence(
            event,
            {
                "route_role": "device_vector",
                "model_required": False,
                "primary_strategy": "provided_path",
                "artifact_required": "preview_svg",
            },
        )
        self.assertEqual(enriched["route_policy_evidence"]["route_role"], "device_vector")


if __name__ == "__main__":
    unittest.main()
