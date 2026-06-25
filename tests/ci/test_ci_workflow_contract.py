import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CI_WORKFLOW = ROOT / ".github" / "workflows" / "ci.yml"
FAKE_INTEGRATION_ENTRIES = (
    (ROOT / "tools" / "fake_u1" / "test_fake_u1.py", "python tools/fake_u1/test_fake_u1.py -v"),
    (ROOT / "tools" / "fake_u1" / "tests" / "test_app.py", "python -m unittest tools.fake_u1.tests.test_app -v"),
    (
        ROOT / "tools" / "fake_device_server" / "tests" / "test_app.py",
        "python -m unittest tools.fake_device_server.tests.test_app -v",
    ),
    (ROOT / "tools" / "fake_ai" / "tests" / "test_app.py", "python -m unittest tools.fake_ai.tests.test_app -v"),
    (ROOT / "tests" / "ci" / "test_fake_integration.py", "python -m unittest tests.ci.test_fake_integration -v"),
)


class CiWorkflowContractTests(unittest.TestCase):
    def test_ci_has_expected_top_level_jobs(self):
        text = CI_WORKFLOW.read_text(encoding="utf-8")

        for job_name in (
            "schema-validate:",
            "gpio-check:",
            "python-unit:",
            "fake-integration:",
                        "manager-mobile-tests:",
            "markdown-link-check:",
        ):
            self.assertIn(job_name, text)

    def test_ci_python_unit_discovers_all_test_files(self):
        text = CI_WORKFLOW.read_text(encoding="utf-8")

        self.assertIn("python-unit:", text)
        self.assertIn('python -m unittest discover -s tests -p "test_*.py" -v', text)

    def test_ci_runs_manager_mobile_typecheck_and_wechat_build(self):
        text = CI_WORKFLOW.read_text(encoding="utf-8")

        self.assertIn("manager-mobile-tests:", text)
        self.assertIn("actions/setup-node@v4", text)
        self.assertIn('node-version: "20"', text)
        self.assertIn("working-directory: server/xiaozhi-esp32-server/main/manager-mobile", text)
        self.assertIn("corepack pnpm install --frozen-lockfile --ignore-scripts", text)
        self.assertIn("corepack pnpm run type-check", text)
        self.assertIn("corepack pnpm run build:mp-weixin", text)
        self.assertIn("Verify WeChat permission artifact", text)
        self.assertIn("dist/build/mp-weixin/app.json", text)
        self.assertIn("scope.record", text)
        self.assertIn("scope.userLocation", text)
        self.assertIn("getLocation", text)

    def test_ci_fake_integration_script_paths_exist(self):
        text = CI_WORKFLOW.read_text(encoding="utf-8")

        for path, command in FAKE_INTEGRATION_ENTRIES:
            with self.subTest(path=path):
                relative = path.relative_to(ROOT).as_posix()
                self.assertTrue(path.exists(), relative)
                self.assertIn(command, text)


if __name__ == "__main__":
    unittest.main()
