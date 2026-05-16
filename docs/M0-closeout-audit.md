# M0 closeout audit

Date: 2026-05-15

## Objective Restatement

Current working objective: learn the project and keep moving the implementation according to `docs/`, project constraints, and global constraints.

## Evidence Boundary

This closeout is local/design-time evidence only. It is not a completion declaration and does not replace remote GitHub Actions, real-board bring-up, hardware validation, or user-approved commit/push review.

Concrete current-stage success criteria before commit/push:

- M0a schema contracts exist, are documented, and validate against examples.
- M0b GPIO static checker reflects current hardware/firmware contracts and has focused tests.
- M0c fake tools let M1-M4 software work proceed without real hardware or real AI providers.
- M0d CI workflow runs the M0a/M0b/M0c checks and includes a real fake U1 integration test.
- M0g design-time hardware audit updates are preserved and open risks remain explicit.
- No commit/push is performed without user permission.

## Prompt-to-Artifact Checklist

| Requirement | Artifact / Evidence | Status | Notes |
|---|---|---|---|
| Use project command constraint | All verification commands run with `rtk ...` | Pass | No non-`rtk` shell commands were used in this round. |
| Do not commit/push without permission | `rtk git status --short --branch --untracked-files=all` shows working tree changes only | Pass | No commit or push performed. |
| M0a: schema set covers Edge-A/B/C/D | `docs/schemas/` contains 26 `*.schema.json`; examples count is 36 | Pass | Verified by file count and `tools/validate_schemas.py`. |
| M0a: Edge-A actual server responses | `authed`, `acked`, `unsubscribed` schema/example files exist | Pass | Also documented in `docs/schemas/edge_a/README.md`. |
| M0a: Edge-D command set includes `GET_DEVICE_INFO` | `docs/schemas/edge_d/cmd.schema.json`; `docs/schemas/edge_d/README.md` | Pass | Response schema for device info is not yet defined; this is documented. |
| M0a: examples validate exactly once | `rtk python tools/validate_schemas.py` -> `validated=62 passed=62 failed=0` | Pass | This covers schema validity and example matching, not runtime behavior. |
| M0b: GPIO checker handles current rules | `tools/check_gpio.py` | Pass | Includes unavailable pins, bidirectional SDA/SCL/DAT/CMD handling, LIMIT input handling, U8/U1 UART guard. |
| M0b: GPIO checker tests | `tools/tests/test_check_gpio.py`; `tools/test_check_gpio.py` | Pass | `rtk python tools/test_check_gpio.py -v` -> 8 tests OK; `rtk python -m unittest tools.tests.test_check_gpio -v` -> 7 tests OK. |
| M0c: fake U1 current lightweight entry | `tools/fake_u1/app.py` | Pass | Supports TCP `@{json}\n`, Edge-D command set, `--inject E001/E005/E006/E008`, `--latency-ms`, soft limits, pause/resume/stop. |
| M0c: fake U1 schema tests | `tools/fake_u1/tests/test_app.py` | Pass | `status/result/error` responses validated against Edge-D schemas. |
| M0c: fake DeviceServer | `tools/fake_device_server/app.py`; tests | Pass | Motion task to U1 command mapping is unit-tested. |
| M0c: fake AI provider | `tools/fake_ai/app.py`; tests | Pass | Deterministic ASR, Edge-B shaped intent payload, silent WAV TTS. |
| M0c: fake tests pass | fake U1 legacy, fake U1 app, fake DeviceServer, fake AI commands | Pass | All focused fake tests passed locally. |
| M0d: CI workflow exists | `.github/workflows/ci.yml` | Pass | Jobs: schema, GPIO, Python unit, fake integration, manager-api tests, manager-mobile tests, markdown link check. |
| M0d: fake integration is real, not placeholder | `tests/ci/test_fake_integration.py` | Pass | Starts `FakeU1TCPServer`, sends real socket frames, validates responses by schema. |
| M0d: local equivalent CI checks pass | command list in `docs/M0d-ci-plan.md` | Pass | All local equivalents passed. |
| M0g: design-time hardware audit preserved | `docs/硬件核对报告.md`; M0g round plans | Pass | §1.1-§1.13 and R-001-R-024 are present per current notes. |
| Docs updated after code | `docs/M0a-schema-plan.md`, `docs/M0b-gpio-check-plan.md`, `docs/M0c-fake-tools-plan.md`, `docs/M0d-ci-plan.md`, `docs/实施计划-v2.md`, `docs/接续指令.md`, `tools/README.md` | Pass | Documentation matches current M0a-M0d state. |

## Verification Commands

Commands run successfully during this closeout:

```bash
rtk python tools/validate_schemas.py
rtk python tools/check_gpio.py
rtk python tools/test_check_gpio.py -v
rtk python -m unittest tools.tests.test_check_gpio -v
rtk python -m unittest discover -s tests -p "test_*.py" -v
rtk python tools/fake_u1/test_fake_u1.py -v
rtk python -m unittest tools.fake_u1.tests.test_app -v
rtk python -m unittest tools.fake_device_server.tests.test_app -v
rtk python -m unittest tools.fake_ai.tests.test_app -v
rtk python -m unittest tests.ci.test_fake_integration -v
rtk powershell.exe -NoProfile -Command "git diff --check"
```

`rtk git diff --check` exits 0 with only LF/CRLF warnings already accepted as non-blocking.

## Known Gaps / Not Complete

- Remote GitHub Actions have not run because no commit/push has been performed.
- M0f real-board bring-up is not done; hardware risks `R-001` through `R-024` remain open until physical validation.
- Edge-D now has dedicated `ack` and `device_info` schemas. Hardware-backed device identity still needs M0f/real firmware verification.
- fake U1 intentionally simulates protocol behavior only; it does not validate real motor physics, Grbl timing, RMT precision, driver current, thermal behavior, or limit-switch electrical behavior.
- `markdown-link-check` was not run locally; the workflow is present, but remote execution remains the real gate.

## Commit Readiness

Ready for user review before commit/push:

- Include all modified files from `rtk git status`.
- Include untracked M0 plan/schema/fake files.
- Do not include generated `__pycache__`; `.gitignore` already excludes them.
- After user approval, commit should be followed by remote Actions verification.
