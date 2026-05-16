# M5 Local Evidence Manifest

Date: 2026-05-16

This manifest maps M5 requirements to local evidence. It is not a completion declaration. Real phone, hardware, production-key, deployment, and production-monitoring gates remain open until release evidence is attached.

## Checklist

| Item | Requirement | Local Evidence | Verification | Open Gate |
| --- | --- | --- | --- | --- |
| M5.1 Provisioning | BLE/BluFi primary provisioning with SoftAP fallback contract | `docs/M5.1-provisioning-status.md`; `firmware/u8-xiaozhi/main/provisioning_contract.h`; `manager-mobile/src/pages/device-config/components/blufi-config.vue`; `manager-mobile/src/pages/device-config/provisioning-contract.ts` | `tests/ci/test_edge_d_firmware_static.py`; `tests/ci/test_manager_mobile_device_info.py` | Real phone-to-U8 BLE provisioning, production BluFi compatibility/encryption, and BLE/SoftAP coexistence on hardware |
| M5.2 AP fallback | SoftAP scan, submit, and exit fallback path | `docs/M5.2-ap-fallback-status.md`; `manager-mobile/src/pages/device-config/components/wifi-config.vue`; `manager-mobile/src/pages/device-config/components/wifi-selector.vue`; `manager-mobile/src/pages/device-config/provisioning-contract.ts` | `tests/ci/test_manager_mobile_device_info.py`; `tests/ci/test_edge_d_firmware_static.py` | Real phone connected to U8 SoftAP, AP fallback after actual BLE failure, and U8 runtime endpoint compatibility |
| M5.3 NVS encryption | Encrypted NVS credential storage design-time contract | `docs/M5.3-nvs-encryption-status.md`; `firmware/u8-xiaozhi/sdkconfig.defaults`; `firmware/u8-xiaozhi/partitions/v2/16m.csv`; `firmware/u8-xiaozhi/main/main.cc`; `firmware/u8-xiaozhi/main/settings.cc` | `tests/ci/test_edge_d_firmware_static.py` | ESP-IDF build with resolved sdkconfig, first-boot key generation, reboot persistence, flash dump inspection, and production eFuse or flash-encryption posture |
| M5.4 OTA client | U8 OTA metadata validation, HTTPS guard, SHA-256, signature, A/B rollback, install-result path | `docs/M5.4-ota-client-status.md`; `firmware/u8-xiaozhi/main/ota.*`; `firmware/u8-xiaozhi/main/application.*`; `firmware/u8-xiaozhi/main/Kconfig.projbuild` | `tests/ci/test_edge_d_firmware_static.py` | Real ESP-IDF build, production OTA certificate chain, production public key provisioning, private-key custody, `ota_0` to `ota_1` hardware OTA, rollback validation, and install-result delivery |
| M5.5 OTA backend | Manager API firmware release planning and xiaozhi-server OTA bridge | `docs/M5.5-ota-backend-status.md`; `FirmwareReleaseService`; `FirmwareReleaseController`; xiaozhi-server OTA release plan bridge; bridge-side BusinessServer plan revalidation for HTTPS URL, 64-character lowercase SHA-256, and base64 signature | `tests/ci/test_manager_api_resource_domain.py`; `tests/ci/test_xiaozhi_server_ota_release_plan.py`; `tests/ci/test_edge_d_firmware_static.py` | Live device consuming a BusinessServer release plan, real post-reboot result POST, deployed monitoring scrape/alert/webhook evidence, production signing evidence, and production public-key provisioning |
| M5.6 Update gate | Manager API and U8 reject task submission while updating/upgrading | `docs/M5.6-update-gate-status.md`; `AppV2ServiceImpl`; `SafetyErrorCode`; U8 board motion-task rejection | `tests/ci/test_manager_api_resource_domain.py`; `tests/ci/test_edge_d_firmware_static.py` | Real hardware OTA timing under WSS task traffic and live xiaozhi-server propagation |
| M5.7 Startup self-check | U8 startup self-check event and xiaozhi-server to manager-api forwarding | `docs/M5.7-self-check-status.md`; U8 `RunStartupSelfCheck`; xiaozhi-server `SelfCheckTextMessageHandler`; manager-api self-check ingest | `tests/ci/test_edge_d_firmware_static.py`; `tests/ci/test_xiaozhi_server_self_check.py`; `tests/ci/test_manager_api_resource_domain.py` | Acoustic loopback, RSSI/internet reachability, and real hardware boot-cycle evidence |
| M5.8 Health-check UI | Mini-program health-check task entry and self-check history display | `docs/M5.8-health-check-ui-status.md`; manager-mobile device detail; manager-api self-check history endpoint | `tests/ci/test_manager_mobile_device_info.py`; `tests/ci/test_manager_api_resource_domain.py`; `tests/ci/test_edge_d_firmware_static.py` | Real U1/mechanism safety for the square path plus production retention policy and dashboard for self-check history |

## Evidence Gap Records

Each status document keeps a structured evidence-gap record for release candidates that lack real-world proof. These records do not replace real-world release evidence.

| Item | Gap Record | Status Document |
| --- | --- | --- |
| M5.1 Provisioning | Provisioning Evidence Gap Record | `docs/M5.1-provisioning-status.md` |
| M5.2 AP fallback | AP Fallback Evidence Gap Record | `docs/M5.2-ap-fallback-status.md` |
| M5.3 NVS encryption | NVS Encryption Evidence Gap Record | `docs/M5.3-nvs-encryption-status.md` |
| M5.4 OTA client | OTA Client Evidence Gap Record | `docs/M5.4-ota-client-status.md` |
| M5.5 OTA backend | OTA Backend Evidence Gap Record | `docs/M5.5-ota-backend-status.md` |
| M5.6 Update gate | Update Gate Evidence Gap Record | `docs/M5.6-update-gate-status.md` |
| M5.7 Startup self-check | Startup Self-Check Evidence Gap Record | `docs/M5.7-self-check-status.md` |
| M5.8 Health-check UI | Health Check UI Evidence Gap Record | `docs/M5.8-health-check-ui-status.md` |

## Completion Rule

Do not mark M5 complete from this manifest alone. M5 is complete only when every open gate above has reviewed real-world evidence attached to the release evidence package.
