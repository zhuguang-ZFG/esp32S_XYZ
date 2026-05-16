import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
M0_CLOSEOUT = ROOT / "docs" / "M0-closeout-audit.md"
M0D_CI_PLAN = ROOT / "docs" / "M0d-ci-plan.md"
M1_EDGE_D_PLAN = ROOT / "docs" / "M1-edge-d-static-contract-plan.md"


class DocsM0M1CloseoutTests(unittest.TestCase):
    def test_m0_closeout_keeps_local_evidence_and_open_hardware_gates_explicit(self):
        text = M0_CLOSEOUT.read_text(encoding="utf-8", errors="replace")

        for token in (
            "M0 closeout audit",
            "learn the project and keep moving the implementation according to `docs/`, project constraints, and global constraints",
            "Evidence Boundary",
            "local/design-time evidence only",
            "not a completion declaration",
            "does not replace remote GitHub Actions, real-board bring-up, hardware validation, or user-approved commit/push review",
            "No commit/push is performed without user permission",
            "M0a: schema set covers Edge-A/B/C/D",
            "contains 26 `*.schema.json`; examples count is 36",
            "validated=62 passed=62 failed=0",
            "M0b: GPIO checker handles current rules",
            "M0c fake tools let M1-M4 software work proceed without real hardware or real AI providers",
            "M0d CI workflow runs the M0a/M0b/M0c checks",
            "manager-api tests, manager-mobile tests",
            "rtk python tools/validate_schemas.py",
            "rtk python tools/check_gpio.py",
            "Remote GitHub Actions have not run because no commit/push has been performed",
            "M0f real-board bring-up is not done",
            "fake U1 intentionally simulates protocol behavior only",
            "does not validate real motor physics",
            "Do not include generated `__pycache__`",
        ):
            self.assertIn(token, text)

    def test_m0_closeout_binds_verification_commands_to_results(self):
        text = M0_CLOSEOUT.read_text(encoding="utf-8", errors="replace")

        for evidence in (
            "`rtk python tools/validate_schemas.py` -> `validated=62 passed=62 failed=0`",
            "`rtk python tools/test_check_gpio.py -v` -> 8 tests OK",
            "`rtk python -m unittest tools.tests.test_check_gpio -v` -> 7 tests OK",
        ):
            with self.subTest(evidence=evidence):
                self.assertIn(evidence, text)

    def test_m0d_ci_plan_tracks_current_api_and_mobile_jobs(self):
        text = M0D_CI_PLAN.read_text(encoding="utf-8", errors="replace")

        for token in (
            "manager-api-tests",
            "expanded App V2 / Edge-A / M5-M6 Maven test slice",
            "manager-mobile-tests",
            "builds the WeChat mini program",
            "firmware release planning",
            "privacy deletion",
            "product notification outbox",
            "monitoring metrics",
            "rtk mvn \"-Dtest=RenExceptionHandlerTest",
            "ProductNotificationOutboxServiceImplTest",
            "MonitoringMetricsControllerTest",
            "rtk corepack pnpm type-check",
            "rtk corepack pnpm run build:mp-weixin",
            "Remote GitHub Actions have not run in this session because no commit/push has been performed",
            "release gating still requires a real remote Actions run",
        ):
            self.assertIn(token, text)

    def test_m1_edge_d_plan_keeps_static_only_limits_explicit(self):
        text = M1_EDGE_D_PLAN.read_text(encoding="utf-8", errors="replace")

        for token in (
            "M1 Edge-D firmware static contract plan",
            "without hardware",
            "Removed `type:\"cmd\"`",
            "Normalized unsupported/invalid private protocol errors to `E009`",
            "Added `accepted:true`",
            "Added minimal `GET_DEVICE_INFO` response",
            "rtk python -m unittest tests.ci.test_edge_d_firmware_static -v",
            "rtk python tools/validate_schemas.py",
            "No hardware was used in this pass",
            "U8 ESP-IDF build was not run in this environment because `idf.py` is not available",
            "Remote GitHub Actions have not been triggered in this session",
            "real hardware identity fields still require M0f/firmware verification",
        ):
            self.assertIn(token, text)


if __name__ == "__main__":
    unittest.main()
