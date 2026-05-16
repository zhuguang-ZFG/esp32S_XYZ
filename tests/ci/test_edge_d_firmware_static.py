import unittest
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
U8_BOARD = ROOT / "firmware" / "u8-xiaozhi" / "main" / "boards" / "zhuguang" / "dlc-motor-control-p1-ai" / "dlc_motor_control_p1_ai_board.cc"
U8_APP_H = ROOT / "firmware" / "u8-xiaozhi" / "main" / "application.h"
U8_APP_CC = ROOT / "firmware" / "u8-xiaozhi" / "main" / "application.cc"
U8_OTA_H = ROOT / "firmware" / "u8-xiaozhi" / "main" / "ota.h"
U8_OTA_CC = ROOT / "firmware" / "u8-xiaozhi" / "main" / "ota.cc"
U8_SETTINGS_CC = ROOT / "firmware" / "u8-xiaozhi" / "main" / "settings.cc"
U8_PROTOCOL_H = ROOT / "firmware" / "u8-xiaozhi" / "main" / "protocols" / "protocol.h"
U8_PROTOCOL_CC = ROOT / "firmware" / "u8-xiaozhi" / "main" / "protocols" / "protocol.cc"
U8_SDKCONFIG_DEFAULTS = ROOT / "firmware" / "u8-xiaozhi" / "sdkconfig.defaults"
U8_MAIN_CC = ROOT / "firmware" / "u8-xiaozhi" / "main" / "main.cc"
U8_KCONFIG = ROOT / "firmware" / "u8-xiaozhi" / "main" / "Kconfig.projbuild"
U8_PARTITION_TABLE = ROOT / "firmware" / "u8-xiaozhi" / "partitions" / "v2" / "16m.csv"
U8_WIFI_BOARD = ROOT / "firmware" / "u8-xiaozhi" / "main" / "boards" / "common" / "wifi_board.cc"
U8_PROVISIONING_CONTRACT = ROOT / "firmware" / "u8-xiaozhi" / "main" / "provisioning_contract.h"
U1_PROTOCOL = ROOT / "firmware" / "u1-grbl" / "Grbl_Esp32" / "src" / "Protocol.cpp"
M5_PROVISIONING_STATUS = ROOT / "docs" / "M5.1-provisioning-status.md"
M5_AP_FALLBACK_STATUS = ROOT / "docs" / "M5.2-ap-fallback-status.md"
M5_NVS_ENCRYPTION_STATUS = ROOT / "docs" / "M5.3-nvs-encryption-status.md"
M5_OTA_CLIENT_STATUS = ROOT / "docs" / "M5.4-ota-client-status.md"
M5_OTA_BACKEND_STATUS = ROOT / "docs" / "M5.5-ota-backend-status.md"
M5_UPDATE_GATE_STATUS = ROOT / "docs" / "M5.6-update-gate-status.md"
M5_SELF_CHECK_STATUS = ROOT / "docs" / "M5.7-self-check-status.md"
M5_HEALTH_CHECK_UI_STATUS = ROOT / "docs" / "M5.8-health-check-ui-status.md"
M5_LOCAL_EVIDENCE_MANIFEST = ROOT / "docs" / "M5-local-evidence-manifest.md"


class EdgeDFirmwareStaticTests(unittest.TestCase):
    def test_u8_default_config_selects_project_board_and_ws_support(self):
        text = U8_SDKCONFIG_DEFAULTS.read_text(encoding="utf-8", errors="replace")
        self.assertIn("CONFIG_BOARD_TYPE_ZHUGUANG_DLC_MOTOR_CONTROL_P1_AI=y", text)
        self.assertIn("CONFIG_HTTPD_WS_SUPPORT=y", text)

    def test_u8_default_config_keeps_ble_primary_and_softap_fallback_provisioning(self):
        text = U8_SDKCONFIG_DEFAULTS.read_text(encoding="utf-8", errors="replace")

        self.assertIn("CONFIG_USE_ESP_BLUFI_WIFI_PROVISIONING=y", text)
        self.assertIn("CONFIG_USE_HOTSPOT_WIFI_PROVISIONING=y", text)

    def test_u8_default_config_enables_nvs_encryption_for_credentials(self):
        text = U8_SDKCONFIG_DEFAULTS.read_text(encoding="utf-8", errors="replace")

        self.assertIn("CONFIG_NVS_ENCRYPTION=y", text)

    def test_u8_default_config_keeps_ota_ab_rollback_enabled(self):
        text = U8_SDKCONFIG_DEFAULTS.read_text(encoding="utf-8", errors="replace")
        partitions = U8_PARTITION_TABLE.read_text(encoding="utf-8", errors="replace")

        self.assertIn("CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE=y", text)
        self.assertRegex(partitions, r"ota_0,\s+app,\s+ota_0,")
        self.assertRegex(partitions, r"ota_1,\s+app,\s+ota_1,")

    def test_u8_ota_release_metadata_requires_https_sha256_and_signature(self):
        ota_h = U8_OTA_H.read_text(encoding="utf-8", errors="replace")
        ota_cc = U8_OTA_CC.read_text(encoding="utf-8", errors="replace")

        for token in (
            "firmware_sha256_",
            "firmware_signature_",
            "firmware_release_id_",
            "GetFirmwareSha256",
            "GetFirmwareSignature",
            "GetFirmwareReleaseId",
            "HasValidFirmwareMetadata",
            'cJSON_GetObjectItem(firmware, "release_id")',
            'cJSON_GetObjectItem(firmware, "sha256")',
            'cJSON_GetObjectItem(firmware, "signature")',
            'url.rfind("https://", 0) == 0',
            "IsLowerHexSha256",
        ):
            self.assertIn(token, ota_h + ota_cc)

    def test_u8_ota_download_verifies_sha256_before_boot_partition_switch(self):
        ota_cc = U8_OTA_CC.read_text(encoding="utf-8", errors="replace")

        for token in (
            "#include <mbedtls/sha256.h>",
            "mbedtls_sha256_starts",
            "mbedtls_sha256_update",
            "mbedtls_sha256_finish",
            "Firmware sha256 mismatch",
            "esp_ota_abort(update_handle)",
            "esp_ota_end(update_handle)",
            "esp_ota_set_boot_partition(update_partition)",
        ):
            self.assertIn(token, ota_cc)
        self.assertLess(ota_cc.index("Firmware sha256 mismatch"), ota_cc.index("esp_ota_end(update_handle)"))

    def test_u8_ota_metadata_signature_is_verified_before_boot_partition_switch(self):
        ota_h = U8_OTA_H.read_text(encoding="utf-8", errors="replace")
        ota_cc = U8_OTA_CC.read_text(encoding="utf-8", errors="replace")
        kconfig = U8_KCONFIG.read_text(encoding="utf-8", errors="replace")

        for token in (
            "CONFIG_OTA_VERIFY_PUBLIC_KEY_PEM",
            "OTA_VERIFY_PUBLIC_KEY_PEM",
            "#include <mbedtls/base64.h>",
            "#include <mbedtls/pk.h>",
            "IsLikelyBase64",
            "DecodeBase64",
            "VerifyFirmwareSignature",
            "mbedtls_base64_decode",
            "mbedtls_pk_parse_public_key",
            "mbedtls_pk_verify",
            "MBEDTLS_MD_SHA256",
            "Firmware signature verification failed",
            "Firmware metadata must include base64 signature",
            "Upgrade(firmware_url_, firmware_sha256_, firmware_signature_, callback)",
        ):
            self.assertIn(token, ota_h + ota_cc + kconfig)
        self.assertLess(ota_cc.index("VerifyFirmwareSignature(digest, expected_signature)"), ota_cc.index("esp_ota_end(update_handle)"))

    def test_u8_application_rejects_ota_when_not_idle_or_activating(self):
        text = U8_APP_CC.read_text(encoding="utf-8", errors="replace")

        self.assertIn("Application::UpgradeFirmware", text)
        self.assertIn("state != kDeviceStateIdle && state != kDeviceStateActivating", text)
        self.assertIn("Rejecting firmware upgrade while state=", text)
        self.assertIn("kDeviceStateUpgrading", text)
        self.assertIn("GetFirmwareSha256()", text)
        self.assertIn("GetFirmwareSignature()", text)
        self.assertIn("Ota::Upgrade(upgrade_url, sha256, signature", text)
        self.assertNotIn("bool upgrade_success = Ota::Upgrade(upgrade_url, [this, display]", text)

    def test_u8_reports_ota_install_result_contract(self):
        ota_h = U8_OTA_H.read_text(encoding="utf-8", errors="replace")
        ota_cc = U8_OTA_CC.read_text(encoding="utf-8", errors="replace")
        app_cc = U8_APP_CC.read_text(encoding="utf-8", errors="replace")
        combined = ota_h + ota_cc + app_cc

        for token in (
            "StorePendingInstallResult",
            "ReportPendingInstallSuccess",
            "ReportInstallFailure",
            "ReportInstallResult",
            "GetInstallResultUrl",
            '"pending_release_id"',
            '"pending_version"',
            '"/install-result"',
            'cJSON_AddStringToObject(root, "release_id"',
            'cJSON_AddBoolToObject(root, "success"',
            'http->Open("POST", GetInstallResultUrl())',
            "ota_->StorePendingInstallResult()",
            'ota_->ReportInstallFailure("upgrade_failed")',
            "ReportPendingInstallSuccess()",
        ):
            self.assertIn(token, combined)
        self.assertLess(app_cc.index("ota_->StorePendingInstallResult()"), app_cc.index("ota_->GetFirmwareUrl()"))
        self.assertLess(ota_cc.index("esp_ota_mark_app_valid_cancel_rollback()"), ota_cc.index("ReportPendingInstallSuccess()"))

    def test_m5_ota_status_docs_keep_production_and_hardware_gates_open(self):
        client = M5_OTA_CLIENT_STATUS.read_text(encoding="utf-8", errors="replace")
        backend = M5_OTA_BACKEND_STATUS.read_text(encoding="utf-8", errors="replace")

        for token in (
            "Real ESP-IDF U8 build after sdkconfig resolution",
            "Real HTTPS certificate-chain validation against production OTA host",
            "production OTA public key and signing private-key custody",
            "CONFIG_OTA_VERIFY_PUBLIC_KEY_PEM",
            "End-to-end OTA from `ota_0` to `ota_1`",
            "`esp_ota_mark_app_valid_cancel_rollback()` confirmation on hardware",
            "Hardware evidence that the post-reboot install-result POST reaches DeviceServer / BusinessServer",
            "production key provisioning and hardware OTA evidence remain open",
            "OTA Client Evidence Gap Record",
            "missing evidence scope: resolved ESP-IDF U8 build, production HTTPS certificate-chain validation, OTA public-key provisioning, signing private-key custody, hardware A/B OTA from `ota_0` to `ota_1`, rollback validation, `esp_ota_mark_app_valid_cancel_rollback()` confirmation, or post-reboot install-result delivery",
            "fallback path: keep OTA labeled design-time, keep manual recovery or serial flashing available, and block production rollout if signature verification, rollback, or install-result delivery is unproven",
            "rollback trigger: ESP-IDF build fails, certificate chain is invalid, public key is missing, signature verification fails, A/B slot switch fails, rollback does not restore the previous app, or install-result delivery is not observed",
            "follow-up evidence: build artifact id, resolved sdkconfig, redacted public-key provisioning record, signing custody approval, OTA metadata, certificate-chain validation output, serial OTA log, `ota_0` to `ota_1` partition evidence, rollback drill log, and DeviceServer / BusinessServer install-result receipt",
        ):
            self.assertIn(token, client)

        for token in (
            "Real deployed DeviceServer / U8 evidence",
            "live device consumes a BusinessServer release plan",
            "Real U8 firmware result POST after reboot / rollback validation on hardware",
            "Real deployed Prometheus scrape health",
            "Real single-device OTA plan hit from a live device",
            "Production signing private-key custody, OTA public-key provisioning, and live signature evidence",
            "xiaozhi-server` revalidates BusinessServer plans against the U8 client contract",
            "BusinessServer `Result.code != 0` as no usable OTA plan",
            "BusinessServer `Result.code != 0` makes the relay report `forwarded = false`",
            "HTTPS URL, 64-character lowercase SHA-256, and base64 signature",
            "latest published release selection is deterministic",
            "`published_at` descending, then `release_id` descending",
            "design-time bridge, not a full rollout console",
            "production OTA should publish signed metadata through BusinessServer",
            "OTA Backend Evidence Gap Record",
            "missing evidence scope: live BusinessServer release-plan consumption, live DeviceServer / U8 upgrade-plan request, deployed install-result relay, rollback validation on hardware, deployed Prometheus scrape health, alert firing, DingTalk or WeCom webhook delivery, single-device plan hit, signing private-key custody, OTA public-key provisioning, or live signature validation",
            "fallback path: keep BusinessServer OTA rollout disabled or dev-channel only, keep `data/bin` fallback limited to development, pause rollout on missing monitoring, and block production if live plan consumption or install-result delivery is unproven",
            "rollback trigger: live device cannot consume a BusinessServer plan, DeviceServer accepts invalid metadata, install-result relay fails, rollback validation fails, failure-rate alert does not fire, webhook delivery is missing, or signing custody / public-key provisioning is unresolved",
            "follow-up evidence: release id, signed metadata record, DeviceServer upgrade-plan request/response, U8 serial log, BusinessServer install-result row or log, rollback drill evidence, Prometheus target screenshot, alert firing screenshot, DingTalk/WeCom delivery screenshot, signing custody record, and public-key provisioning record",
        ):
            self.assertIn(token, backend)

    def test_u8_settings_commits_nvs_erase_operations(self):
        text = U8_SETTINGS_CC.read_text(encoding="utf-8", errors="replace")

        self.assertIn("void Settings::EraseKey", text)
        self.assertIn("ret == ESP_OK", text)
        self.assertIn("dirty_ = true;", text)
        self.assertLess(text.index("ret == ESP_OK"), text.index("ret != ESP_ERR_NVS_NOT_FOUND"))
        erase_all = text[text.index("void Settings::EraseAll"):]
        self.assertIn("nvs_erase_all", erase_all)
        self.assertIn("dirty_ = true;", erase_all)

    def test_u8_rejects_motion_tasks_while_upgrading(self):
        text = U8_BOARD.read_text(encoding="utf-8", errors="replace")

        self.assertIn("EmitMotionEventError", text)
        self.assertIn("Application::GetInstance().GetDeviceState() == kDeviceStateUpgrading", text)
        self.assertIn('"E_DEVICE_UPDATING"', text)
        self.assertIn('"device is updating"', text)
        self.assertLess(text.index("kDeviceStateUpgrading"), text.index('cap_norm == "home"'))

    def test_u8_startup_self_check_emits_required_checks(self):
        app_h = U8_APP_H.read_text(encoding="utf-8", errors="replace")
        app_cc = U8_APP_CC.read_text(encoding="utf-8", errors="replace")
        protocol_h = U8_PROTOCOL_H.read_text(encoding="utf-8", errors="replace")
        protocol_cc = U8_PROTOCOL_CC.read_text(encoding="utf-8", errors="replace")
        board = U8_BOARD.read_text(encoding="utf-8", errors="replace")

        for token in (
            "RunStartupSelfCheck",
            "SendSelfCheck",
            '"self_check"',
            '"check_id", "startup"',
            '"nvs"',
            '"wifi"',
            '"u1_uart"',
            '"audio"',
        ):
            self.assertIn(token, app_h + app_cc + protocol_h + protocol_cc + board)
        self.assertIn("nvs_get_stats", app_cc)
        self.assertIn("board.CheckU1Uart", app_cc)
        self.assertIn("ExecuteGetStatusWithTaskId(\"self_check_u1\")", board)
        self.assertIn("protocol_->SendSelfCheck", app_cc)

    def test_m5_update_self_check_status_docs_keep_hardware_and_deployment_gates_open(self):
        update_gate = M5_UPDATE_GATE_STATUS.read_text(encoding="utf-8", errors="replace")
        self_check = M5_SELF_CHECK_STATUS.read_text(encoding="utf-8", errors="replace")
        health_check = M5_HEALTH_CHECK_UI_STATUS.read_text(encoding="utf-8", errors="replace")

        for token in (
            "Real hardware OTA timing under WSS task traffic",
            "End-to-end propagation through a live xiaozhi-server deployment",
            "E_DEVICE_UPDATING",
            'phase:"failed"',
            "Update Gate Evidence Gap Record",
            "missing evidence scope: real hardware OTA timing under WSS task traffic, live xiaozhi-server propagation, manager-api rejection before task persistence, U8 `motion_task` rejection while upgrading, or terminal `phase:\"failed\"` delivery with `E_DEVICE_UPDATING`",
            "fallback path: pause OTA rollout while active task traffic is unverified, keep manual task submission disabled during upgrade windows, and block release if either backend or firmware gate is bypassed",
            "rollback trigger: manager-api persists or forwards a task while the device is updating, U8 accepts a `motion_task` in `kDeviceStateUpgrading`, terminal failure event is missing, or live xiaozhi-server does not propagate `E_DEVICE_UPDATING`",
        ):
            self.assertIn(token, update_gate)

        for token in (
            "Audio check verifies codec readiness and sample-rate configuration, not acoustic loopback",
            "Wi-Fi check verifies the active network object after connection, not RSSI or internet reachability",
            "Later M5.8 work adds local diagnostic persistence in `device_self_check_events`",
            "latest five diagnostics through `POST /api/v1/devices/{deviceId}/self-check/history`",
            "No real hardware boot cycle was performed",
            "Startup Self-Check Evidence Gap Record",
            "missing evidence scope: real hardware boot cycle, U8 startup `self_check` emission, U1 UART `GET_STATUS` response, codec readiness, Wi-Fi active-network validation, xiaozhi-server forwarding, manager-api ingestion, Edge-A broadcast, or persisted `device_self_check_events` history visibility",
            "fallback path: keep startup self-check labeled design-time, keep manual health-check entry visible, and block release if boot diagnostics are absent or cannot reach mobile history",
            "rollback trigger: startup event is not emitted after boot, any required check is missing, U1 UART check hangs or reports unsafe state, xiaozhi-server forwarding fails, manager-api ingestion fails, or mobile cannot load the latest diagnostic history",
        ):
            self.assertIn(token, self_check)
        self.assertNotIn("No diagnostic persistence table is added in this slice", self_check)

        for token in (
            "Backend now persists `self_check` events in `device_self_check_events`",
            "latest five diagnostics of a bound device",
            "supersedes the earlier local limitation text",
            "Real U1/mechanism safety for the square health-check path still needs hardware verification",
            "no production retention policy or dashboard has been deployed for this table yet",
            "Health Check UI Evidence Gap Record",
            "missing evidence scope: real U1/mechanism safety for the square health-check path, mobile health-check button drill, `run_path` submission, `self_check` event rendering, diagnostic history retrieval, production retention policy, or dashboard visibility for `device_self_check_events`",
            "fallback path: keep health-check UI labeled local/static, keep the square path conservative, hide or disable production-facing health-check claims if hardware safety or history retention is unproven, and block release if unsafe movement is observed",
            "rollback trigger: square health-check movement is unsafe, `run_path` submission fails, live `self_check` event does not render, history endpoint omits recent diagnostics, retention policy is missing for production, or dashboard visibility is required but unavailable",
        ):
            self.assertIn(token, health_check)

    def test_m5_local_evidence_manifest_maps_items_to_open_gates(self):
        text = M5_LOCAL_EVIDENCE_MANIFEST.read_text(encoding="utf-8", errors="replace")

        for token in (
            "M5 Local Evidence Manifest",
            "not a completion declaration",
            "M5.1 Provisioning",
            "M5.2 AP fallback",
            "M5.3 NVS encryption",
            "M5.4 OTA client",
            "M5.5 OTA backend",
            "M5.6 Update gate",
            "M5.7 Startup self-check",
            "M5.8 Health-check UI",
            "Evidence Gap Records",
            "These records do not replace real-world release evidence",
            "Provisioning Evidence Gap Record",
            "AP Fallback Evidence Gap Record",
            "NVS Encryption Evidence Gap Record",
            "OTA Client Evidence Gap Record",
            "OTA Backend Evidence Gap Record",
            "Update Gate Evidence Gap Record",
            "Startup Self-Check Evidence Gap Record",
            "Health Check UI Evidence Gap Record",
            "Real phone-to-U8 BLE provisioning",
            "production BluFi compatibility/encryption",
            "flash dump inspection",
            "`ota_0` to `ota_1` hardware OTA",
            "Live device consuming a BusinessServer release plan",
            "bridge-side BusinessServer plan revalidation for HTTPS URL, 64-character lowercase SHA-256, and base64 signature",
            "production public-key provisioning",
            "Real hardware OTA timing under WSS task traffic",
            "real hardware boot-cycle evidence",
            "Real U1/mechanism safety for the square path",
            "Do not mark M5 complete from this manifest alone",
        ):
            self.assertIn(token, text)

    def test_u8_partition_table_reserves_nvs_keys_before_ota_apps(self):
        text = U8_PARTITION_TABLE.read_text(encoding="utf-8", errors="replace")

        self.assertRegex(text, r"nvs,\s+data,\s+nvs,\s+0x9000,\s+0x4000,")
        self.assertRegex(text, r"nvs_keys,\s+data,\s+nvs_keys,\s+0xd000,\s+0x1000,\s+encrypted")
        self.assertRegex(text, r"otadata,\s+data,\s+ota,\s+0xe000,\s+0x2000,")
        self.assertRegex(text, r"phy_init,\s+data,\s+phy,\s+0x10000,\s+0x1000,")
        self.assertRegex(text, r"ota_0,\s+app,\s+ota_0,\s+0x20000,\s+0x3f0000,")

    def test_u8_nvs_init_uses_secure_keys_with_plain_fallback(self):
        text = U8_MAIN_CC.read_text(encoding="utf-8", errors="replace")

        for token in (
            "CONFIG_NVS_ENCRYPTION",
            "ESP_PARTITION_SUBTYPE_DATA_NVS_KEYS",
            "nvs_flash_read_security_cfg",
            "ESP_ERR_NVS_KEYS_NOT_INITIALIZED",
            "ESP_ERR_NVS_CORRUPT_KEY_PART",
            "nvs_flash_generate_keys",
            "nvs_flash_secure_init",
            "nvs_flash_init();",
            "nvs_flash_erase()",
            "InitNvsStorage()",
        ):
            self.assertIn(token, text)

    def test_u8_provisioning_contract_names_channels_endpoints_and_nvs_keys(self):
        text = U8_PROVISIONING_CONTRACT.read_text(encoding="utf-8", errors="replace")

        for token in (
            'kPrimaryChannel = "ble_blufi"',
            'kFallbackChannel = "softap_http"',
            'kBlufiDeviceName = "Xiaozhi-Blufi"',
            'kSoftApBaseUrl = "http://192.168.4.1"',
            'kSoftApScanPath = "/scan"',
            'kSoftApSubmitPath = "/submit"',
            'kSoftApExitPath = "/exit"',
            'kNvsSsidKey = "ssid"',
            'kNvsPasswordKey = "password"',
            'kNvsDeviceSecretKey = "device_secret"',
            'kNvsServerHostKey = "server_host"',
            'kSecurityPairing = "ble_just_works"',
        ):
            self.assertIn(token, text)

    def test_u8_wifi_config_mode_starts_ble_before_softap_fallback(self):
        text = U8_WIFI_BOARD.read_text(encoding="utf-8", errors="replace")

        self.assertIn('#include "provisioning_contract.h"', text)
        self.assertIn("ProvisioningContract::kSoftApSsidPrefix", text)
        self.assertIn("Blufi::GetInstance().init();", text)
        self.assertIn("wifi_manager.StartConfigAp();", text)
        self.assertLess(
            text.index("Blufi::GetInstance().init();"),
            text.index("wifi_manager.StartConfigAp();"),
        )
        self.assertIn("kDeviceStateWifiConfiguring", text)

    def test_m5_provisioning_status_docs_keep_real_device_gates_open(self):
        provisioning = M5_PROVISIONING_STATUS.read_text(encoding="utf-8", errors="replace")
        ap_fallback = M5_AP_FALLBACK_STATUS.read_text(encoding="utf-8", errors="replace")
        nvs = M5_NVS_ENCRYPTION_STATUS.read_text(encoding="utf-8", errors="replace")

        for token in (
            "Real phone-to-U8 BLE provisioning test",
            "Production Espressif BluFi frame compatibility",
            "encryption handshake",
            "Runtime resolution of Blufi and SoftAP coexistence",
            "NVS encryption enforcement and verification for Wi-Fi credentials, `device_secret`, and `server_host`",
            "AP fallback end-to-end validation after BLE failure",
            "Provisioning Evidence Gap Record",
            "missing evidence scope: phone-to-U8 BLE provisioning, BluFi frame compatibility, encryption handshake, real-device response handling, BLE/SoftAP coexistence, NVS encrypted credential persistence, or AP fallback after BLE failure",
            "fallback path: keep SoftAP fallback available, keep provisioning labeled design-time until hardware evidence is attached, and block release if neither BLE nor SoftAP has a passing real-device path",
            "rollback trigger: BLE scan/connect/write fails, BluFi handshake fails, SoftAP fails after BLE failure, credentials are not persisted, or plaintext credentials are found in storage evidence",
        ):
            self.assertIn(token, provisioning)

        for token in (
            "Real phone connected to U8 SoftAP and successful credential submit",
            "AP fallback after an actual BLE failure",
            "U8-side runtime verification that `/scan`, `/submit`, and `/exit` remain compatible",
            "NVS encrypted persistence verification after AP fallback provisioning",
            "AP Fallback Evidence Gap Record",
            "missing evidence scope: phone joins U8 SoftAP, scan response, credential submit, exit request, AP fallback after BLE failure, U8 endpoint compatibility, or NVS encrypted persistence after SoftAP provisioning",
            "fallback path: keep manual Wi-Fi retry instructions visible, keep BLE provisioning available, and block release if SoftAP is the only remaining provisioning path and lacks evidence",
            "rollback trigger: phone cannot join SoftAP, `/scan` shape is incompatible, `/submit` fails, `/exit` fails, BLE failure does not expose fallback, or credentials are not persisted",
        ):
            self.assertIn(token, ap_fallback)

        for token in (
            "ESP-IDF build for the U8 target with this exact sdkconfig resolution",
            "Real-device first boot key generation in `nvs_keys`",
            "Reboot persistence after writing WiFi credentials and `device_secret`",
            "Flash dump inspection proving plaintext credentials are not present",
            "does not provision eFuse keys or enable production flash encryption",
            "NVS Encryption Evidence Gap Record",
            "missing evidence scope: resolved ESP-IDF sdkconfig, first-boot `nvs_keys` generation, credential write, reboot persistence, flash dump inspection, flash encryption posture, HMAC/eFuse key-protection posture, or production key-provisioning procedure",
            "fallback path: keep credentials treated as not production-proven, avoid storing production secrets on unverified firmware, and block release if plaintext credentials are found or key-protection posture is unresolved",
            "rollback trigger: sdkconfig disables NVS encryption, `nvs_keys` is missing, reboot loses credentials, flash dump contains plaintext credential material, or production eFuse/flash-encryption posture is not approved",
        ):
            self.assertIn(token, nvs)

    def test_u8_command_builder_matches_cmd_schema_shape(self):
        text = U8_BOARD.read_text(encoding="utf-8", errors="replace")
        self.assertIn('"msg_id"', text)
        self.assertIn('"task_id"', text)
        self.assertIn('"cmd"', text)
        self.assertNotIn('"type":"cmd"', text)
        self.assertNotIn('\\"type\\":\\"cmd\\"', text)

    def test_u8_exposes_get_device_info_contract(self):
        text = U8_BOARD.read_text(encoding="utf-8", errors="replace")
        self.assertIn('"GET_DEVICE_INFO"', text)
        self.assertIn("ExecuteGetDeviceInfoWithTaskId", text)
        self.assertIn("self.motor.get_device_info", text)
        self.assertIn('"model"', text)
        self.assertIn('"hw_rev"', text)
        self.assertIn('"fw_rev"', text)
        self.assertIn('"workspace_mm"', text)
        self.assertIn("EmitDeviceInfoIfOk", text)
        self.assertIn("SendDeviceInfo", text)

    def test_u8_exposes_voice_safe_control_tools(self):
        text = U8_BOARD.read_text(encoding="utf-8", errors="replace")
        for tool_name, cmd in (
            ("self.motor.pause", "PAUSE"),
            ("self.motor.resume", "RESUME"),
            ("self.motor.stop", "STOP"),
        ):
            self.assertIn(f'"{tool_name}"', text)
            self.assertIn(f'"{cmd}"', text)
        self.assertIn("ExecuteControlWithTaskId", text)
        self.assertIn("ExecutePauseCapability", text)
        self.assertIn("ExecuteResumeCapability", text)
        self.assertIn("ExecuteStopCapability", text)
        self.assertIn('cap_norm == "pause"', text)
        self.assertIn('cap_norm == "resume"', text)
        self.assertIn('cap_norm == "stop"', text)

    def test_u8_exposes_whitelisted_relative_move_tool(self):
        text = U8_BOARD.read_text(encoding="utf-8", errors="replace")
        self.assertIn('"self.motor.move_rel"', text)
        self.assertIn("ExecuteMoveRelWithTaskId", text)
        self.assertIn("ExecuteMoveRelCapability", text)
        self.assertIn('cap_norm == "move_rel"', text)
        self.assertIn('"GET_STATUS"', text)
        self.assertIn('"GET_DEVICE_INFO"', text)
        self.assertIn('"position"', text)
        self.assertIn('dx < -1 || dx > 1', text)
        self.assertIn('target outside workspace', text)
        self.assertIn('Property("dx", kPropertyTypeInteger, 0, -1, 1)', text)
        self.assertIn('Property("dy", kPropertyTypeInteger, 0, -1, 1)', text)
        self.assertIn('Property("dz", kPropertyTypeInteger, 0, -1, 1)', text)

    def test_u8_device_info_wss_frame_is_explicit(self):
        board = U8_BOARD.read_text(encoding="utf-8", errors="replace")
        app_h = U8_APP_H.read_text(encoding="utf-8", errors="replace")
        app_cc = U8_APP_CC.read_text(encoding="utf-8", errors="replace")
        protocol_h = U8_PROTOCOL_H.read_text(encoding="utf-8", errors="replace")
        protocol_cc = U8_PROTOCOL_CC.read_text(encoding="utf-8", errors="replace")

        self.assertIn("void SendDeviceInfo(cJSON* fields)", app_h)
        self.assertIn("void Application::SendDeviceInfo", app_cc)
        self.assertIn("void SendDeviceInfo(cJSON* fields)", protocol_h)
        self.assertIn("void Protocol::SendDeviceInfo", protocol_cc)
        self.assertIn('"type", "device_info"', protocol_cc)
        self.assertIn("Application::GetInstance().SendDeviceInfo", board)

    def test_u8_motion_event_preserves_task_source_for_tts_hints(self):
        text = U8_BOARD.read_text(encoding="utf-8", errors="replace")
        self.assertIn("last_motion_source_", text)
        self.assertIn('cJSON_GetObjectItemCaseSensitive(root, "source")', text)
        self.assertIn('cJSON_AddStringToObject(o, "source"', text)

    def test_u8_run_path_emits_segment_progress_events(self):
        text = U8_BOARD.read_text(encoding="utf-8", errors="replace")
        self.assertIn("EmitMotionEventProgress", text)
        self.assertIn('cJSON_AddStringToObject(o, "phase", "progress")', text)
        self.assertIn('"done_segments"', text)
        self.assertIn('"total_segments"', text)
        self.assertIn('"percent"', text)
        self.assertIn("RunPathWithTaskId(task_id, path_json, feed_rate, true)", text)

    def test_u1_error_codes_are_edge_d_schema_codes(self):
        text = U1_PROTOCOL.read_text(encoding="utf-8", errors="replace")
        self.assertNotIn('"E010"', text)
        self.assertNotIn('"E003"', text)

    def test_u1_supports_get_device_info_contract(self):
        text = U1_PROTOCOL.read_text(encoding="utf-8", errors="replace")
        self.assertIn('"GET_DEVICE_INFO"', text)
        self.assertIn('\\"model\\"', text)
        self.assertIn('\\"hw_rev\\"', text)
        self.assertIn('\\"fw_rev\\"', text)
        self.assertIn('\\"workspace_mm\\"', text)

    def test_u1_ack_carries_accepted_flag(self):
        text = U1_PROTOCOL.read_text(encoding="utf-8", errors="replace")
        self.assertIn('\\"type\\":\\"ack\\"', text)
        self.assertIn('\\"accepted\\":true', text)
        ack_lines = [line for line in text.splitlines() if '\\"type\\":\\"ack\\"' in line]
        self.assertGreaterEqual(len(ack_lines), 1)
        for line in ack_lines:
            self.assertRegex(line, re.escape('\\"accepted\\":true'))


if __name__ == "__main__":
    unittest.main()
