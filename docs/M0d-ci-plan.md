# M0d CI plan

Date: 2026-05-15

## Scope

M0d wires the design-time checks from M0a/M0b/M0c into GitHub Actions so regressions are visible before hardware arrives.

## Workflow

Updated `.github/workflows/ci.yml` jobs:

- `schema-validate`: installs `jsonschema` and runs `rtk python tools/validate_schemas.py`.
- `gpio-check`: runs `rtk python tools/check_gpio.py`.
- `python-unit`: runs GPIO unit tests and repository CI tests under `tests/`.
- `fake-integration`: installs `jsonschema`, then runs fake U1 legacy tests, fake U1 schema tests, fake DeviceServer tests, fake AI tests, and socket-level fake U1 integration tests.
- `manager-api-tests`: uses Java 21 and runs the expanded App V2 / Edge-A / M5-M6 Maven test slice.
- `manager-mobile-tests`: uses Node 20, installs pnpm dependencies, runs type-check, and builds the WeChat mini program.
- `markdown-link-check`: keeps the existing markdown link checker.

## Coverage Notes

- `tests/ci/test_fake_integration.py` now starts `FakeU1TCPServer` on an ephemeral local port and sends real `@{json}\n` frames through TCP.
- The fake integration test validates happy paths, path pause/resume/stop, error paths, Edge-D `GET_DEVICE_INFO`, and fake DeviceServer HTTP `get_device_info` forwarding to fake U1.
- `status`, `result`, and `error` responses are checked against Edge-D schemas.
- Edge-D now has dedicated `ack` and `device_info` schemas; fake integration validates both response shapes.
- `manager-api-tests` covers task ingest/status persistence, idempotent task submit, DeviceServer motion_task gateway forwarding, internal motion/device info endpoints, Edge-A seq/replay/ack, App V2 WSS event payloads, firmware release planning, privacy deletion, consumables, primary session, device transfer, RMA, product notification outbox, and monitoring metrics.
- `manager-mobile-tests` covers the TypeScript surface, WeChat mini-program build, and generated `dist/build/mp-weixin/app.json` permission artifact for the mobile provisioning, device detail, privacy, and M6 UI flows.

## Current Limitation

Remote GitHub Actions have not run in this session because no commit/push has been performed. Local commands and static workflow checks are evidence for the CI contract only; release gating still requires a real remote Actions run after the changes are pushed with user approval.

## Local Verification

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
rtk mvn "-Dtest=RenExceptionHandlerTest,SafetyValidatorTest,SafetyAuditServiceTest,ContentAuditServiceTest,ContentAuditLogServiceTest,SingleLineSvgValidatorTest,WriteTextProjectionServiceTest,DrawGeneratedProjectionServiceTest,FactoryEntitlementServiceTest,ResourceEntitlementServiceTest,FirmwareReleaseServiceTest,FirmwareReleaseControllerTest,MemberServiceImplTest,VoiceprintEnrollmentServiceImplTest,PrivacyDeletionServiceImplTest,DeviceSupplyServiceImplTest,PrimarySessionServiceImplTest,DeviceTransferServiceImplTest,DeviceRmaServiceImplTest,ProductNotificationOutboxServiceImplTest,MonitoringMetricsServiceImplTest,MonitoringMetricsControllerTest,AppV2ServiceImplTest,AppV2ControllerTest,InternalMotionEventControllerTest,ConfigControllerTest,DeviceServerMotionGatewayImplTest,EdgeAClientHubTest,ClientEdgeWebSocketHandlerTest" test
rtk corepack pnpm type-check
rtk corepack pnpm run build:mp-weixin
```
