import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RUNBOOKS = ROOT / "ops" / "runbooks"
DOCS = ROOT / "docs"
OPS = ROOT / "ops"
STEERING = ROOT / ".kiro" / "steering"
TOOLS = ROOT / "tools"
TASK_INDEX = DOCS / "编码任务索引-v2.md"
CORE_PROJECT_DOCS = (
    DOCS / "编码任务索引-v2.md",
    DOCS / "实施计划-v2.md",
    DOCS / "全局规划-Planning-with-Files.md",
    DOCS / "接续指令.md",
    DOCS / "M5-local-evidence-manifest.md",
    DOCS / "M5.1-provisioning-status.md",
    DOCS / "M5.2-ap-fallback-status.md",
    DOCS / "M5.3-nvs-encryption-status.md",
    DOCS / "M5.4-ota-client-status.md",
    DOCS / "M5.5-ota-backend-status.md",
    DOCS / "M5.6-update-gate-status.md",
    DOCS / "M5.7-self-check-status.md",
    DOCS / "M5.8-health-check-ui-status.md",
)


POWERSHELL_BLOCK = re.compile(r"```powershell\n(?P<body>.*?)\n```", re.DOTALL)
FENCED_SHELL_BLOCK = re.compile(
    r"```(?P<lang>bash|sh|shell|powershell|ps1)\s*\n(?P<body>.*?)\n```",
    re.DOTALL | re.IGNORECASE,
)
ARROW_SHELL_COMMAND = re.compile(
    r"(?:->|\u2192)\s*(?:python |mvn |corepack |node |git |docker |powershell\.exe |pnpm |rg )",
    re.IGNORECASE,
)
INLINE_CODE = re.compile(r"`([^`\n]+)`")
EVIDENCE_GAP_RECORD_HEADING = re.compile(r"^## .*Evidence Gap Record\s*$", re.MULTILINE)
SHELL_COMMAND_PREFIXES = (
    "python ",
    "mvn ",
    "corepack ",
    "node ",
    "git ",
    "docker ",
    "powershell.exe ",
    "pnpm ",
    "rg ",
)
COMPLETION_GUARD_TERMS = (
    "not a completion declaration",
    "do not mark",
    "not evidence",
    "does not replace",
)
EVIDENCE_GAP_RECORD_TERMS = (
    "owner:",
    "due date:",
    "fallback path:",
    "risk acceptance:",
    "rollback trigger:",
    "follow-up evidence:",
)


def _completion_guard_docs():
    docs = []
    for path in DOCS.rglob("*.md"):
        name = path.name.lower()
        if (
            name.endswith("-status.md")
            or name.endswith("manifest.md")
            or "closeout" in name
        ):
            docs.append(path)
    docs.extend(sorted(RUNBOOKS.glob("*.md")))
    return sorted(docs)


def _docs_and_runbooks():
    return sorted(list(DOCS.rglob("*.md")) + list(RUNBOOKS.glob("*.md")))


def _command_contract_docs():
    docs = set(DOCS.rglob("*.md"))
    docs.update(OPS.rglob("*.md"))
    docs.update(STEERING.glob("*.md"))
    docs.update(TOOLS.rglob("*.md"))
    docs.update(ROOT.glob("*.md"))
    return sorted(docs)


class RunbookCommandContractTests(unittest.TestCase):
    def test_powershell_runbook_commands_use_rtk_prefix(self):
        for path in sorted(RUNBOOKS.glob("*.md")):
            text = path.read_text(encoding="utf-8", errors="replace")
            for block in POWERSHELL_BLOCK.finditer(text):
                for line in block.group("body").splitlines():
                    command = line.strip()
                    if not command:
                        continue
                    with self.subTest(runbook=path.name, command=command):
                        self.assertTrue(command.startswith("rtk "), command)

    def test_tests_ci_commands_document_repository_root_cwd(self):
        for path in sorted(RUNBOOKS.glob("*.md")):
            text = path.read_text(encoding="utf-8", errors="replace")
            for block in POWERSHELL_BLOCK.finditer(text):
                body = block.group("body")
                if "tests.ci." not in body:
                    continue

                context = text[max(0, block.start() - 300):block.start()].lower()
                with self.subTest(runbook=path.name, command=body.strip()):
                    self.assertIn("repository root", context)

    def test_repository_relative_ops_paths_document_repository_root_or_secret_override(self):
        for path in sorted(RUNBOOKS.glob("*.md")):
            text = path.read_text(encoding="utf-8", errors="replace")
            for block in POWERSHELL_BLOCK.finditer(text):
                body = block.group("body")
                if "ops/" not in body:
                    continue

                context = text[max(0, block.start() - 400):block.start()].lower()
                with self.subTest(runbook=path.name, command=body.strip()):
                    self.assertTrue(
                        "repository root" in context or "deployment-managed secret" in context,
                        context,
                    )

    def test_manager_api_relative_source_paths_document_manager_api_cwd(self):
        for path in sorted(RUNBOOKS.glob("*.md")):
            text = path.read_text(encoding="utf-8", errors="replace")
            for block in POWERSHELL_BLOCK.finditer(text):
                body = block.group("body")
                if "src/main/java" not in body:
                    continue

                context = text[max(0, block.start() - 300):block.start()].lower()
                with self.subTest(runbook=path.name, command=body.strip()):
                    self.assertIn("manager-api", context)

    def test_m6_doc_inline_shell_commands_use_rtk_prefix(self):
        for path in sorted(DOCS.glob("M6*.md")):
            text = path.read_text(encoding="utf-8", errors="replace")
            for match in INLINE_CODE.finditer(text):
                snippet = match.group(1).strip()
                lower = snippet.lower()
                if not lower.startswith(SHELL_COMMAND_PREFIXES):
                    continue

                with self.subTest(doc=path.name, command=snippet):
                    self.fail(f"inline shell command must use rtk prefix: {snippet}")

    def test_runbook_inline_shell_commands_use_rtk_prefix(self):
        for path in sorted(RUNBOOKS.glob("*.md")):
            text = path.read_text(encoding="utf-8", errors="replace")
            for match in INLINE_CODE.finditer(text):
                snippet = match.group(1).strip()
                lower = snippet.lower()
                if not lower.startswith(SHELL_COMMAND_PREFIXES):
                    continue

                with self.subTest(runbook=path.name, command=snippet):
                    self.fail(f"inline shell command must use rtk prefix: {snippet}")

    def test_core_project_docs_inline_shell_commands_use_rtk_prefix(self):
        for path in CORE_PROJECT_DOCS:
            text = path.read_text(encoding="utf-8", errors="replace")
            for match in INLINE_CODE.finditer(text):
                snippet = match.group(1).strip()
                lower = snippet.lower()
                if not lower.startswith(SHELL_COMMAND_PREFIXES):
                    continue

                with self.subTest(doc=path.name, command=snippet):
                    self.fail(f"inline shell command must use rtk prefix: {snippet}")

    def test_all_docs_inline_shell_commands_use_rtk_prefix(self):
        for path in sorted(DOCS.rglob("*.md")):
            text = path.read_text(encoding="utf-8", errors="replace")
            for match in INLINE_CODE.finditer(text):
                snippet = match.group(1).strip()
                lower = snippet.lower()
                if not lower.startswith(SHELL_COMMAND_PREFIXES):
                    continue

                with self.subTest(doc=str(path.relative_to(ROOT)), command=snippet):
                    self.fail(f"inline shell command must use rtk prefix: {snippet}")

    def test_steering_inline_shell_commands_use_rtk_prefix(self):
        for path in sorted(STEERING.glob("*.md")):
            text = path.read_text(encoding="utf-8", errors="replace")
            for match in INLINE_CODE.finditer(text):
                snippet = match.group(1).strip()
                lower = snippet.lower()
                if not lower.startswith(SHELL_COMMAND_PREFIXES):
                    continue

                with self.subTest(doc=str(path.relative_to(ROOT)), command=snippet):
                    self.fail(f"inline shell command must use rtk prefix: {snippet}")

    def test_fenced_shell_commands_use_rtk_prefix(self):
        for path in _command_contract_docs():
            text = path.read_text(encoding="utf-8", errors="replace")
            for block in FENCED_SHELL_BLOCK.finditer(text):
                for line in block.group("body").splitlines():
                    command = line.strip()
                    lower = command.lower()
                    if not lower.startswith(SHELL_COMMAND_PREFIXES):
                        continue

                    with self.subTest(doc=str(path.relative_to(ROOT)), command=command):
                        self.fail(f"fenced shell command must use rtk prefix: {command}")

    def test_arrow_shell_command_examples_use_rtk_prefix(self):
        for path in _command_contract_docs():
            for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
                if not ARROW_SHELL_COMMAND.search(line):
                    continue

                with self.subTest(doc=str(path.relative_to(ROOT)), line=line.strip()):
                    self.fail(f"arrow shell command example must use rtk prefix: {line.strip()}")

    def test_status_manifest_closeout_and_runbook_docs_keep_completion_guard(self):
        for path in _completion_guard_docs():
            text = path.read_text(encoding="utf-8", errors="replace").lower()
            with self.subTest(doc=str(path.relative_to(ROOT))):
                self.assertTrue(
                    any(term in text for term in COMPLETION_GUARD_TERMS),
                    f"{path} must state that local evidence does not close external or completion gates",
                )

    def test_evidence_gap_records_have_release_tracking_fields(self):
        for path in _docs_and_runbooks():
            text = path.read_text(encoding="utf-8", errors="replace")
            if not EVIDENCE_GAP_RECORD_HEADING.search(text):
                continue

            lower = text.lower()
            for term in EVIDENCE_GAP_RECORD_TERMS:
                with self.subTest(doc=str(path.relative_to(ROOT)), term=term):
                    self.assertIn(term, lower)


if __name__ == "__main__":
    unittest.main()
