# M6 Local Evidence Manifest

Date: 2026-05-16

This manifest maps M6 requirements to local evidence. It is not a completion declaration. External, hardware, deployment, and production-operation gates remain open until real-world evidence is attached to the release evidence package.

## Checklist

| Item | Requirement | Local Evidence | Verification | Open Gate |
| --- | --- | --- | --- | --- |
| M6.1 Privacy permissions | Privacy page plus separate microphone, Bluetooth, and Wi-Fi permission flows | `docs/M6.1-privacy-permissions-status.md`; `manager-mobile/manifest.config.ts`; `manager-mobile/vite.config.ts`; `manager-mobile/src/manifest.json`; generated `dist/build/mp-weixin/app.json`; `manager-mobile/src/pages/settings/privacy-permissions.vue`; `manager-mobile/src/pages/device-config/index.vue`; `ops/runbooks/m6-privacy-permissions.md` | `tests/ci/test_manager_mobile_privacy_permissions.py`; `rtk corepack pnpm type-check`; `rtk corepack pnpm run build:mp-weixin`; generated permission artifact check | WeChat console privacy declarations, real permission prompts, and review approval |
| M6.2 Data deletion | Voiceprint deletion, account deletion, retention fields, retention cleanup, deleted-account tombstone old-token refusal | `docs/M6.2-privacy-deletion-status.md`; `PrivacyDeletionServiceImpl`; `PrivacyRetentionCleanupTask`; `AppV2ServiceImpl`; `DeviceSupplyServiceImpl`; `MemberServiceImpl`; `VoiceprintEnrollmentServiceImpl`; `DeviceRmaServiceImpl`; `ops/runbooks/m6-retention-cleanup.md` | `tests/ci/test_manager_api_privacy_deletion.py`; `PrivacyDeletionServiceImplTest`; `AppV2ServiceImplTest`; `DeviceSupplyServiceImplTest`; `MemberServiceImplTest`; `VoiceprintEnrollmentServiceImplTest`; `DeviceRmaServiceImplTest`; `ContentAuditLogServiceTest`; `SafetyAuditServiceTest` | Production scheduling and post-run deletion logs |
| M6.3 Compliance readiness | ICP, MLPS, PIPL, AI provider, hardware/OTA, signoff checklist | `docs/M6.3-release-compliance-checklist.md`; `ops/runbooks/m6-release-evidence-package.md`; release package requirements for internal runtime endpoint smoke and Manager API scrape-secret evidence | `tests/ci/test_docs_m6_compliance_checklist.py` | ICP filing, MLPS assessment, WeChat review, legal signoff, hardware certification, and reviewed production-operation evidence |
| M6.4 Primary session | Single-writer primary session, per-voice-task approval, and WebSocket token/account revalidation | `docs/M6.4-primary-session-status.md`; `PrimarySessionServiceImpl`; `AppV2ServiceImpl`; `AppV2Controller`; `ClientEdgeWebSocketHandler`; manager-mobile pending voice approval UI; native tabBar badge helper and App `onShow` badge restore; payload-minimized `ProductNotificationOutboxService`; safe provider payload builder; provider sent/failed markers; `ops/runbooks/m6-product-notifications.md` | `tests/ci/test_manager_api_primary_session.py`; `tests/ci/test_manager_mobile_device_info.py`; `PrimarySessionServiceImplTest`; `AppV2ServiceImplTest`; `AppV2ControllerTest`; `ClientEdgeWebSocketHandlerTest`; `ProductNotificationOutboxServiceImplTest` | Platform push delivery, native tabBar rendering on real WeChat clients, and background reminders or release deferral with owner, due date, fallback path, and risk acceptance |
| M6.5 Device transfer | Transfer request, accept, cancel, old history retention, cache clearing | `docs/M6.5-device-transfer-status.md`; `DeviceTransferServiceImpl`; `DeviceServerMotionGatewayImpl`; payload-minimized `ProductNotificationOutboxService`; safe provider payload builder; provider sent/failed markers; manager-mobile device detail/list pages; native tabBar badge helper and App `onShow` badge restore; `ops/runbooks/m6-product-notifications.md` | `tests/ci/test_manager_api_device_transfer.py`; `tests/ci/test_manager_mobile_device_info.py`; `DeviceTransferServiceImplTest`; `DeviceServerMotionGatewayImplTest`; `AppV2ControllerTest`; `ProductNotificationOutboxServiceImplTest` | Platform push delivery, native tabBar rendering on real WeChat clients, and background reminders or release deferral with owner, due date, fallback path, and risk acceptance |
| M6.6 Consumables | Manual consumable state, paper gate, pen mileage estimate | `docs/M6.6-consumables-status.md`; `DeviceSupplyServiceImpl`; `AppV2ServiceImpl`; manager-mobile device detail page; `ops/runbooks/m6-consumables-hardware.md` | `tests/ci/test_manager_api_consumables.py`; `tests/ci/test_manager_mobile_device_info.py`; `DeviceSupplyServiceImplTest`; `AppV2ServiceImplTest`; `AppV2ControllerTest` | Physical sensing, ink/pressure sensor integration, and real-device mileage accuracy with recorded `run_path` payload, mileage calculation, and accepted tolerance |
| M6.7 Device RMA | Repair, return, restock, dispose, disposed-device runtime refusal | `docs/M6.7-device-rma-status.md`; `DeviceRmaServiceImpl`; `device_rma_events` local audit rows with `ticketRef`; `factoryCleaned`/`evidenceRef` restock-dispose gate; `AppV2Controller` super-admin/RMA-only permission gate and local audit export endpoint; `InternalMotionEventController`; `ShiroConfig` internal endpoint pass-through to controller Bearer-token auth; `ConfigController`; xiaozhi-server disposed auth check; `ops/runbooks/m6-device-rma.md` | `tests/ci/test_manager_api_device_rma.py`; `tests/ci/test_xiaozhi_server_disposed_auth.py`; `tests/ci/test_manager_api_resource_domain.py`; `DeviceRmaServiceImplTest`; `AppV2ControllerTest`; `InternalMotionEventControllerTest`; `ConfigControllerTest` | Production operator permission assignment, gateway policy, exported audit evidence review, physical factory credential cleaning proof, deployed DeviceServer refusal drill |
| M6.8 Monitoring alerts | Prometheus metrics, server-secret protected scrape, Grafana dashboard, Alertmanager routes, product notification outbox queue visibility | `docs/M6.8-monitoring-alerts-status.md`; `MonitoringMetricsController`; `MonitoringMetricsServiceImpl`; `ShiroConfig`; `ops/monitoring/prometheus.yml`; `ops/monitoring/secrets/.gitignore`; `ops/runbooks/m6-monitoring-alerts.md`; provider failed markers | `tests/ci/test_monitoring_alerts.py`; `MonitoringMetricsServiceImplTest`; `MonitoringMetricsControllerTest` | Deployed scrape health, redacted secret mount evidence, 401 refusal for missing or wrong scrape Bearer token, dashboard rendering, DingTalk/WeCom alert delivery |

## Runbooks

- `ops/runbooks/m6-privacy-permissions.md`
- `ops/runbooks/m6-retention-cleanup.md`
- `ops/runbooks/m6-release-evidence-package.md`
- `ops/runbooks/m6-product-notifications.md`
- `ops/runbooks/m6-consumables-hardware.md`
- `ops/runbooks/m6-device-rma.md`
- `ops/runbooks/m6-monitoring-alerts.md`

## Runbook Gap Records

Each runbook keeps the release-candidate gap or deferral structure for evidence that cannot be proven locally. These records do not replace external approval, hardware evidence, deployed monitoring, or production-operation evidence.

| Area | Gap Or Deferral Record | Runbook |
| --- | --- | --- |
| M6.1 Privacy permissions | Privacy Permissions Evidence Gap Record | `ops/runbooks/m6-privacy-permissions.md` |
| M6.2 Retention cleanup | Retention Evidence Gap Record | `ops/runbooks/m6-retention-cleanup.md` |
| M6.3 Release evidence package | External evidence gap rule: owner, due date, fallback path, risk acceptance, rollback trigger, and follow-up evidence | `ops/runbooks/m6-release-evidence-package.md` |
| M6.4/M6.5 Product notifications | Release Deferral Record | `ops/runbooks/m6-product-notifications.md` |
| M6.6 Consumables hardware | Consumables Evidence Gap Record | `ops/runbooks/m6-consumables-hardware.md` |
| M6.7 Device RMA | RMA Evidence Gap Record | `ops/runbooks/m6-device-rma.md` |
| M6.8 Monitoring alerts | Monitoring Evidence Gap Record | `ops/runbooks/m6-monitoring-alerts.md` |

## Completion Rule

Do not mark M6 complete from this manifest alone. M6 is complete only when the release evidence package contains reviewed evidence for every open gate above.
