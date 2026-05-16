# M6 Closeout Audit

Date: 2026-05-16

## Objective

This audit records the current M6 implementation status against `docs/实施计划-v2.md` and the project constraint that local code work must stay honest about external, hardware, deployment, and product-operation gaps.

The prompt-to-artifact map is maintained in `docs/M6-local-evidence-manifest.md`.

It is not a completion declaration. M6 has strong local backend, mobile, documentation, and static verification evidence, but several launch gates cannot be closed from this workspace alone.

## Scope Checklist

| Item | Plan requirement | Local evidence | Current audit status |
| --- | --- | --- | --- |
| M6.1 Privacy policy and permissions | Mini-program privacy page; separate microphone, Bluetooth, and Wi-Fi permission flows; fallback prompts when unauthorized | `docs/M6.1-privacy-permissions-status.md`; `manager-mobile/manifest.config.ts`; `manager-mobile/vite.config.ts`; `manager-mobile/src/manifest.json`; `manager-mobile/src/pages/settings/privacy-permissions.vue`; `manager-mobile/src/pages/device-config/index.vue`; `ops/runbooks/m6-privacy-permissions.md`; `tests/ci/test_manager_mobile_privacy_permissions.py` | Local mini-program side, generated WeChat build permission artifact, and release acceptance runbook are covered. WeChat backend privacy configuration, real permission prompts, and real review evidence are still external. |
| M6.2 Data deletion and sensitive-data minimization | Voiceprint deletion; account deletion entry; audit retention fields; one account-deletion and one voiceprint-deletion drill; deleted-account tombstone refusal | `docs/M6.2-privacy-deletion-status.md`; `PrivacyDeletionServiceImpl`; `PrivacyRetentionCleanupTask`; `AppV2ServiceImpl`; `DeviceSupplyServiceImpl`; `MemberServiceImpl`; `VoiceprintEnrollmentServiceImpl`; `DeviceRmaServiceImpl`; `AppV2Controller`; `db/changelog/202605151900.sql`; `ops/runbooks/m6-retention-cleanup.md`; manager-mobile settings page; `PrivacyDeletionServiceImplTest`; `tests/ci/test_manager_api_privacy_deletion.py` | Minimal deletion, anonymization, retention-expiry voiceprint physical cleanup, expired account tombstone clearing, mobile double-confirmation account deletion UI, old-token mutation refusal for deleted account tombstones, and retention cleanup operations runbook are covered. Account rows are preserved for historical joins. |
| M6.3 Compliance readiness | ICP and MLPS 2.0 readiness checklist; no claim that external processes are completed | `docs/M6.3-compliance-readiness-status.md`; `docs/M6.3-release-compliance-checklist.md`; `docs/M6-local-evidence-manifest.md`; `ops/runbooks/m6-release-evidence-package.md`; `tests/ci/test_docs_m6_compliance_checklist.py` | Checklist, local evidence manifest, and release evidence package runbook exist and are test-covered. ICP filing, MLPS assessment, WeChat review, legal signoff, and hardware certification remain external launch gates. |
| M6.4 Primary-session arbitration | Primary session; non-primary read-only behavior; conflicting writes rejected when two clients are online; WebSocket token/account revalidation | `docs/M6.4-primary-session-status.md`; `PrimarySessionServiceImpl`; `AppV2ServiceImpl`; `AppV2Controller`; `ClientEdgeWebSocketHandler`; `RenExceptionHandler`; manager-mobile device detail page; native tabBar badge helper; App `onShow` badge restore; `ProductNotificationOutboxService.buildSafeProviderPayload`; `ProductNotificationOutboxService.markProviderSent`; `ProductNotificationOutboxService.markProviderFailed`; `ops/runbooks/m6-product-notifications.md`; `202605160100.sql`; `202605160130.sql`; `202605160200.sql`; `PrimarySessionServiceImplTest`; `AppV2ServiceImplTest`; `AppV2ControllerTest`; `ClientEdgeWebSocketHandlerTest`; `ProductNotificationOutboxServiceImplTest`; `tests/ci/test_manager_api_primary_session.py`; `tests/ci/test_manager_mobile_device_info.py` | Write arbitration, voice-write gate, 60 second primary lease release, WebSocket token revalidation and deleted-account tombstone refusal, per-voice-task primary approve/reject backend protocol, pull-based mobile pending-approval UI, in-app pending count badge, best-effort native tabBar badge update and App `onShow` restore, payload-minimized local pending notification outbox with resolved/cancelled lifecycle closure, safe provider payload builder, provider sent/failed markers, and platform-notification acceptance runbook are covered locally. Platform push delivery and background reminders remain unproven. |
| M6.5 Device transfer | Device can move from old account to new account; old-account history remains on old account | `docs/M6.5-device-transfer-status.md`; `DeviceTransferServiceImpl`; `AppV2Controller`; `DeviceServerMotionGatewayImpl`; xiaozhi-server voiceprint cache clear handler; manager-mobile device detail/list pages; native tabBar badge helper; App `onShow` badge restore; `ProductNotificationOutboxService.buildSafeProviderPayload`; `ProductNotificationOutboxService.markProviderSent`; `ProductNotificationOutboxService.markProviderFailed`; `ops/runbooks/m6-product-notifications.md`; `202605160200.sql`; `DeviceTransferServiceImplTest`; `AppV2ControllerTest`; `DeviceServerMotionGatewayImplTest`; `ProductNotificationOutboxServiceImplTest`; `tests/ci/test_manager_api_device_transfer.py`; `tests/ci/test_manager_mobile_device_info.py` | Backend transfer, accept, cancel, pending incoming list, old-history retention, cache-clear contracts, mobile pull-based pending incoming entry, in-app pending count badge, best-effort native tabBar badge update and App `onShow` restore, payload-minimized local pending notification outbox with resolved/cancelled lifecycle closure, safe provider payload builder, provider sent/failed markers, and platform-notification acceptance runbook are covered. Platform push delivery and background reminders remain unproven. |
| M6.6 Consumables | `paper_slot_state`, `pen_installed_at`, `pen_ink_percent_est`; mobile manual state maintenance; `write_text/draw` blocked by paper gate | `docs/M6.6-consumables-status.md`; `DeviceSupplyServiceImpl`; `AppV2ServiceImpl`; manager-mobile device detail page; `ops/runbooks/m6-consumables-hardware.md`; `DeviceSupplyServiceImplTest`; `AppV2ServiceImplTest`; `AppV2ControllerTest`; `tests/ci/test_manager_api_consumables.py`; `tests/ci/test_manager_mobile_device_info.py` | Manual consumable state, paper soft gate, completed `run_path` mileage accumulation, and hardware acceptance runbook are covered, including recorded `run_path` payload, expected mileage calculation, and accepted tolerance requirements for real-device evidence. Ink and paper sensing are estimates/manual only, and `unknown` paper state is intentionally allowed through. |
| M6.7 Device RMA | Bound, repair, returned, and disposed states; one repair flow and one return flow | `docs/M6.7-device-rma-status.md`; `DeviceRmaServiceImpl`; `device_rma_events`; `ticketRef`; `factoryCleaned`/`evidenceRef` gate; `AppV2Controller`; `InternalMotionEventController`; `ShiroConfig`; `ConfigController`; xiaozhi-server WebSocket auth check; RMA migration; `ops/runbooks/m6-device-rma.md`; `DeviceRmaServiceImplTest`; `AppV2ControllerTest`; `InternalMotionEventControllerTest`; `ConfigControllerTest`; `tests/ci/test_manager_api_device_rma.py`; `tests/ci/test_xiaozhi_server_disposed_auth.py`; `tests/ci/test_manager_api_resource_domain.py` | Backend RMA flow, `sys:role:superAdmin` or narrower `appv2:device:rma` operator/admin gate, local factory-cleaning attestation gate, local structured RMA audit rows with `ticketRef`, local audit export endpoint, Shiro pass-through for internal runtime endpoints before controller Bearer-token auth, BusinessServer internal runtime refusal, xiaozhi-server proactive WebSocket auth refusal for disposed devices, and RMA operations runbook are covered. Production role assignment, gateway policy, exported audit evidence review, and physical factory credential cleaning still need production execution evidence. |
| M6.8 Monitoring alerts | Prometheus + Grafana; DingTalk/WeCom alert channels; task failure, device offline, and OTA failure alerts | `docs/M6.8-monitoring-alerts-status.md`; `MonitoringMetricsController`; `MonitoringMetricsServiceImpl`; `ShiroConfig`; `ops/monitoring`; `ops/runbooks/m6-monitoring-alerts.md`; `MonitoringMetricsServiceImplTest`; `MonitoringMetricsControllerTest`; `tests/ci/test_monitoring_alerts.py` | Metrics endpoint, server-secret protected scrape contract, alert config artifacts, dashboard provisioning, monitoring alert drill runbook, device lifecycle count visibility, and local product notification outbox queue visibility backed by provider failed markers are covered statically. Prometheus/Grafana/Alertmanager are not deployed in CI, and webhook URLs plus Manager API scrape secret files remain deployment secrets. |

## Recent Verification Evidence

The following focused checks were reported passing during the M6 implementation pass:

- Latest continuation evidence:
  - `rtk python -m unittest discover -s tests -p "test_*.py" -v` -> `243 tests OK`.
  - `rtk mvn "-Dtest=AppV2ServiceImplTest,ClientEdgeWebSocketHandlerTest,PrivacyDeletionServiceImplTest,DeviceTransferServiceImplTest,DeviceSupplyServiceImplTest,MemberServiceImplTest,VoiceprintEnrollmentServiceImplTest,DeviceRmaServiceImplTest,ProductNotificationOutboxServiceImplTest,InternalMotionEventControllerTest,ConfigControllerTest,MonitoringMetricsServiceImplTest,MonitoringMetricsControllerTest" test` -> `119 tests OK`.
  - `rtk python -m unittest tests.ci.test_ci_workflow_contract -v` -> `5 tests OK`.
  - `rtk python -m unittest tests.ci.test_runbook_command_contract -v` -> `13 tests OK`.
  - `rtk python -m unittest tests.ci.test_docs_m6_compliance_checklist -v` -> `10 tests OK`.
  - `rtk git diff --check -- ops/runbooks/m6-release-evidence-package.md tests/ci/test_docs_m6_compliance_checklist.py`.

- Earlier focused continuation evidence from this pass:
  - `rtk mvn "-Dtest=AppV2ServiceImplTest,ClientEdgeWebSocketHandlerTest,PrivacyDeletionServiceImplTest,DeviceTransferServiceImplTest,DeviceSupplyServiceImplTest,MemberServiceImplTest,VoiceprintEnrollmentServiceImplTest,DeviceRmaServiceImplTest" test` -> `91 tests OK`.
  - `rtk python -m unittest discover -s tests -p "test_*.py" -v` -> `222 tests OK`.
  - `rtk python -m unittest tests.ci.test_monitoring_alerts -v` -> `9 tests OK`.
  - `rtk mvn "-Dtest=MonitoringMetricsServiceImplTest,MonitoringMetricsControllerTest" test` -> `3 tests OK`.
  - `rtk git diff --check -- server/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/security/config/ShiroConfig.java ops/monitoring/prometheus.yml ops/monitoring/docker-compose.yml ops/monitoring/README.md ops/monitoring/secrets/.gitignore ops/runbooks/m6-monitoring-alerts.md docs/M6.8-monitoring-alerts-status.md tests/ci/test_monitoring_alerts.py`.
  - `rtk python -m unittest tests.ci.test_manager_api_resource_domain -v` -> `24 tests OK`.
  - `rtk mvn "-Dtest=InternalMotionEventControllerTest,ConfigControllerTest,MonitoringMetricsControllerTest" test` -> `18 tests OK`.

- Earlier broad local regression evidence from this pass:
  - `rtk python -m unittest discover -s tests -p "test_*.py" -v` -> `211 tests OK`.
  - `rtk python tools/validate_schemas.py` -> `validated=62 passed=62 failed=0`.
  - `rtk python tools/check_gpio.py`.
  - `rtk python tools/test_check_gpio.py -v` -> `8 tests OK`.
  - `rtk python -m unittest tools.tests.test_check_gpio -v` -> `7 tests OK`.
  - `rtk mvn "-Dtest=RenExceptionHandlerTest,SafetyValidatorTest,SafetyAuditServiceTest,ContentAuditServiceTest,ContentAuditLogServiceTest,SingleLineSvgValidatorTest,WriteTextProjectionServiceTest,DrawGeneratedProjectionServiceTest,FactoryEntitlementServiceTest,ResourceEntitlementServiceTest,FirmwareReleaseServiceTest,FirmwareReleaseControllerTest,MemberServiceImplTest,VoiceprintEnrollmentServiceImplTest,PrivacyDeletionServiceImplTest,DeviceSupplyServiceImplTest,PrimarySessionServiceImplTest,DeviceTransferServiceImplTest,DeviceRmaServiceImplTest,ProductNotificationOutboxServiceImplTest,MonitoringMetricsServiceImplTest,MonitoringMetricsControllerTest,AppV2ServiceImplTest,AppV2ControllerTest,InternalMotionEventControllerTest,ConfigControllerTest,DeviceServerMotionGatewayImplTest,EdgeAClientHubTest,ClientEdgeWebSocketHandlerTest" test` -> `188 tests OK`.
  - `rtk corepack pnpm type-check`.
  - `rtk corepack pnpm run build:mp-weixin` -> `DONE Build complete`.
  - `rtk node -e "...dist/build/mp-weixin/app.json..."` from `server/xiaozhi-esp32-server/main/manager-mobile` -> `scope.record,scope.userLocation`.

- `rtk python -m unittest tests.ci.test_manager_api_primary_session -v`
- `rtk mvn "-Dtest=AppV2ServiceImplTest,PrimarySessionServiceImplTest,InternalMotionEventControllerTest" test`
- `rtk python -m unittest tests.ci.test_manager_api_consumables -v`
- `rtk mvn "-Dtest=DeviceSupplyServiceImplTest,AppV2ServiceImplTest" test`
- `rtk python -m unittest tests.ci.test_manager_api_device_transfer -v`
- `rtk mvn "-Dtest=DeviceTransferServiceImplTest,AppV2ControllerTest" test`
- `rtk python -m unittest tests.ci.test_manager_mobile_device_info -v`
- `rtk corepack pnpm type-check` from `server/xiaozhi-esp32-server/main/manager-mobile`
- `rtk python -m unittest tests.ci.test_manager_mobile_privacy_permissions tests.ci.test_manager_api_consumables tests.ci.test_docs_m6_compliance_checklist -v`
- `rtk mvn "-Dtest=DeviceSupplyServiceImplTest,AppV2ServiceImplTest,AppV2ControllerTest" test`
- `rtk python -m unittest tests.ci.test_manager_mobile_device_info tests.ci.test_docs_m6_compliance_checklist -v`
- `rtk python -m unittest tests.ci.test_manager_api_device_rma tests.ci.test_xiaozhi_server_disposed_auth tests.ci.test_docs_m6_compliance_checklist -v`
- `rtk mvn "-Dtest=DeviceRmaServiceImplTest,AppV2ControllerTest,InternalMotionEventControllerTest,ConfigControllerTest" test`
- `rtk python -m unittest tests.ci.test_monitoring_alerts tests.ci.test_docs_m6_compliance_checklist -v`
- `rtk mvn "-Dtest=MonitoringMetricsServiceImplTest,MonitoringMetricsControllerTest" test`
- `rtk python -m unittest tests.ci.test_monitoring_alerts -v`
- `rtk mvn "-Dtest=MonitoringMetricsServiceImplTest,MonitoringMetricsControllerTest" test`
- `rtk python -m unittest tests.ci.test_manager_api_privacy_deletion tests.ci.test_docs_m6_compliance_checklist -v`
- `rtk mvn "-Dtest=PrivacyDeletionServiceImplTest,ContentAuditLogServiceTest,SafetyAuditServiceTest" test`
- `rtk python -m unittest tests.ci.test_docs_m6_compliance_checklist -v`
- `rtk python -m unittest tests.ci.test_manager_api_primary_session tests.ci.test_manager_api_device_transfer tests.ci.test_manager_mobile_device_info -v`
- `rtk mvn "-Dtest=AppV2ServiceImplTest,DeviceTransferServiceImplTest,ProductNotificationOutboxServiceImplTest" test`
- `rtk git diff --check -- server/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/appv2/service/ProductNotificationOutboxService.java server/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/appv2/service/impl/ProductNotificationOutboxServiceImpl.java server/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/appv2/service/impl/AppV2ServiceImpl.java server/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/appv2/service/impl/DeviceTransferServiceImpl.java server/xiaozhi-esp32-server/main/manager-api/src/main/resources/db/changelog/202605160200.sql server/xiaozhi-esp32-server/main/manager-api/src/test/java/xiaozhi/modules/appv2/service/ProductNotificationOutboxServiceImplTest.java server/xiaozhi-esp32-server/main/manager-api/src/test/java/xiaozhi/modules/appv2/service/AppV2ServiceImplTest.java server/xiaozhi-esp32-server/main/manager-api/src/test/java/xiaozhi/modules/appv2/service/DeviceTransferServiceImplTest.java tests/ci/test_manager_api_primary_session.py tests/ci/test_manager_api_device_transfer.py tests/ci/test_manager_mobile_device_info.py ops/runbooks/m6-product-notifications.md docs/M6.4-primary-session-status.md docs/M6.5-device-transfer-status.md docs/M6-closeout-audit.md`
- `rtk mvn "-Dtest=AppV2ServiceImplTest,DeviceTransferServiceImplTest,ProductNotificationOutboxServiceImplTest,MonitoringMetricsServiceImplTest,MonitoringMetricsControllerTest" test`
- `rtk python -m unittest tests.ci.test_manager_api_primary_session tests.ci.test_manager_api_device_transfer tests.ci.test_manager_mobile_device_info tests.ci.test_monitoring_alerts tests.ci.test_docs_m6_compliance_checklist -v`
- `rtk python -m unittest tests.ci.test_manager_mobile_privacy_permissions tests.ci.test_manager_api_privacy_deletion tests.ci.test_docs_m6_compliance_checklist tests.ci.test_manager_api_primary_session tests.ci.test_manager_api_device_transfer tests.ci.test_manager_api_consumables tests.ci.test_manager_api_device_rma tests.ci.test_manager_mobile_device_info tests.ci.test_monitoring_alerts -v`
- `rtk mvn "-Dtest=PrivacyDeletionServiceImplTest,ContentAuditLogServiceTest,SafetyAuditServiceTest,DeviceSupplyServiceImplTest,AppV2ServiceImplTest,DeviceTransferServiceImplTest,DeviceRmaServiceImplTest,ProductNotificationOutboxServiceImplTest,MonitoringMetricsServiceImplTest,MonitoringMetricsControllerTest" test`

The broader M6 regression subset was also reported passing for:

- Python CI: primary session, device transfer, consumables, device RMA, and monitoring alerts.
- Maven: `AppV2ServiceImplTest`, `PrimarySessionServiceImplTest`, `DeviceTransferServiceImplTest`, `DeviceSupplyServiceImplTest`, `AppV2ControllerTest`, `InternalMotionEventControllerTest`, `DeviceRmaServiceImplTest`, `MonitoringMetricsServiceImplTest`, and `MonitoringMetricsControllerTest`.
- Runbooks: privacy permissions, retention cleanup, product notifications, consumables hardware, device RMA, monitoring alerts, and release evidence package are statically guarded by focused Python tests.
- Runbook command contracts: `tests/ci/test_runbook_command_contract.py` guards `rtk` prefixes, repository-root `tests.ci` commands, repository-relative `ops/` paths, and manager-api source-path commands.
- Manifest: `docs/M6-local-evidence-manifest.md` maps every M6 item to local evidence, verification, and open gates.
- Release package: `ops/runbooks/m6-release-evidence-package.md` requires reviewed M5/M6 local manifests plus concrete production-operation evidence for platform notifications, native tabBar screenshots, background reminders or release deferral with owner, due date, fallback path, and risk acceptance, retention post-run logs, RMA operator/audit/refusal drills, internal runtime endpoint smoke with Bearer-token refusal/acceptance evidence, Manager API scrape-secret config and 401 refusal evidence, monitoring delivery, and consumables hardware sensing plus recorded mileage payload/calculation/tolerance evidence or limitation signoff.

## Residual Launch Gaps

1. External compliance is not closed locally: ICP, MLPS 2.0, WeChat mini-program review, legal signoff, hardware certification, and release signoff have a release evidence package contract, but still need real-world evidence.
2. Hardware and deployment behavior is not fully exercised: consumable sensing and mileage accuracy with recorded path payload/calculation/tolerance evidence, factory credential cleanup execution, manager-api-backed disposed-device WebSocket refusal in a deployed DeviceServer, and deployed monitoring scrape/dashboard/alert delivery need environment validation.
3. Product workflows need polish before launch: pending transfer and primary-session voice approval platform push notifications and background reminder surfaces have a runbook contract, and native tabBar badge update/restore code exists locally, but delivery/rendering evidence or explicit deferral owner, due date, fallback path, and risk acceptance is still required.
4. Operational lifecycle jobs still need deployment evidence: privacy, content-audit, and safety-audit retention tasks and runbooks exist locally, but production scheduling and post-run logs are not yet proven.

## Conclusion

M6 is locally advanced enough to serve as an implementation baseline for backend contracts, mobile entry points, status documentation, and static/targeted regression checks. It is not ready to be marked complete as a launch milestone until the residual external, hardware, deployment, and product-operation gates above are closed with evidence.
