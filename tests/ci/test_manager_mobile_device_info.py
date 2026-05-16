import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEVICE_DETAIL = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-mobile"
    / "src"
    / "pages"
    / "v2"
    / "device-detail"
    / "index.vue"
)
V2_API = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-mobile"
    / "src"
    / "api"
    / "v2"
    / "index.ts"
)
DEVICE_LIST = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-mobile"
    / "src"
    / "pages"
    / "v2"
    / "device-list"
    / "index.vue"
)
DEVICE_CONFIG_PAGE = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-mobile"
    / "src"
    / "pages"
    / "device-config"
    / "index.vue"
)
DEVICE_CONFIG_WIFI_CONFIG = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-mobile"
    / "src"
    / "pages"
    / "device-config"
    / "components"
    / "wifi-config.vue"
)
DEVICE_CONFIG_WIFI_SELECTOR = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-mobile"
    / "src"
    / "pages"
    / "device-config"
    / "components"
    / "wifi-selector.vue"
)
DEVICE_CONFIG_BLUFI_CONFIG = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-mobile"
    / "src"
    / "pages"
    / "device-config"
    / "components"
    / "blufi-config.vue"
)
DEVICE_CONFIG_CONTRACT = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-mobile"
    / "src"
    / "pages"
    / "device-config"
    / "provisioning-contract.ts"
)
PAGES_JSON = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-mobile"
    / "src"
    / "pages.json"
)
PRODUCT_NOTIFICATIONS_RUNBOOK = ROOT / "ops" / "runbooks" / "m6-product-notifications.md"
UTILS = ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-mobile" / "src" / "utils" / "index.ts"
APP_VUE = ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-mobile" / "src" / "App.vue"


class ManagerMobileDeviceInfoTests(unittest.TestCase):
    def test_device_detail_handles_device_info_reply_event(self):
        text = DEVICE_DETAIL.read_text(encoding="utf-8", errors="replace")

        self.assertIn("handleEdgeAEvent(m.event)", text)
        self.assertIn("event?.event_type === 'device_info_reply'", text)
        self.assertIn("applyDeviceInfoReply(event, payload as DeviceInfoReplyPayload)", text)
        self.assertIn("payload?.hw_rev", text)
        self.assertIn("payload?.fw_rev", text)
        self.assertIn("payload?.workspace_mm", text)
        self.assertIn("workspaceMm", text)

    def test_device_detail_can_submit_get_device_info_task(self):
        text = DEVICE_DETAIL.read_text(encoding="utf-8", errors="replace")

        self.assertIn("handleRefreshInfo", text)
        self.assertIn("v2SubmitTask(deviceId.value, 'get_device_info')", text)
        self.assertIn("latestDeviceInfoTaskId", text)
        self.assertIn("刷新信息", text)

    def test_device_detail_displays_run_path_progress_events(self):
        text = DEVICE_DETAIL.read_text(encoding="utf-8", errors="replace")

        self.assertIn("latestProgressPercent", text)
        self.assertIn("applyTaskProgress(jobPayload)", text)
        self.assertIn("payload.phase === 'progress'", text)
        self.assertIn("payload.phase === 'accepted' || payload.phase === 'running'", text)
        self.assertIn("latestPhase.value === 'progress'", text)
        self.assertIn("progressBarStyle", text)
        self.assertIn("latestProgressLabel", text)

    def test_device_detail_prompts_retry_when_runtime_status_is_refreshing(self):
        text = DEVICE_DETAIL.read_text(encoding="utf-8", errors="replace")

        self.assertIn("runtimeStatusRefreshMessage", text)
        self.assertIn("设备状态正在刷新，请稍后重试", text)
        self.assertIn("taskSubmitErrorMessage", text)
        self.assertIn("text.includes('E_RUNTIME_STALE')", text)
        self.assertIn("message.alert(taskSubmitErrorMessage(e))", text)

    def test_device_detail_shows_child_friendly_content_audit_message(self):
        text = DEVICE_DETAIL.read_text(encoding="utf-8", errors="replace")

        self.assertIn("contentBlockedMessage", text)
        self.assertIn("内容不适合绘制，请换一段文字或图案", text)
        self.assertIn("text.includes('E_CONTENT_BLOCKED')", text)
        self.assertIn("message.alert(taskSubmitErrorMessage(e))", text)

    def test_device_detail_shows_friendly_invalid_drawing_message(self):
        text = DEVICE_DETAIL.read_text(encoding="utf-8", errors="replace")

        self.assertIn("invalidDrawingMessage", text)
        self.assertIn("图案暂时无法绘制，请换一个更简单的图案", text)
        self.assertIn("text.includes('E_INVALID_DRAWING')", text)
        self.assertIn("message.alert(taskSubmitErrorMessage(e))", text)

    def test_device_detail_shows_friendly_entitlement_message(self):
        text = DEVICE_DETAIL.read_text(encoding="utf-8", errors="replace")

        self.assertIn("entitlementRequiredMessage", text)
        self.assertIn("E_NOT_ENTITLED", text)
        self.assertIn("text.includes('E_NOT_ENTITLED')", text)
        self.assertIn("message.alert(taskSubmitErrorMessage(e))", text)

    def test_device_detail_can_manually_maintain_supplies_state(self):
        text = DEVICE_DETAIL.read_text(encoding="utf-8", errors="replace")
        api = V2_API.read_text(encoding="utf-8", errors="replace")

        self.assertIn("v2UpdateDeviceSupplies", api)
        self.assertIn("`/api/v1/devices/${deviceId}/supplies`", api)
        self.assertIn("deviceSupplies", text)
        self.assertIn("updatePaperSlotState", text)
        self.assertIn("markNewPenInstalled", text)
        self.assertIn("paperSlotState: 'empty' | 'loaded' | 'unknown'", (ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-mobile" / "src" / "api" / "v2" / "types.ts").read_text(encoding="utf-8", errors="replace"))
        self.assertIn("updatePaperSlotState('loaded')", text)
        self.assertIn("updatePaperSlotState('empty')", text)
        self.assertIn("resetPenMileage: true", text)
        self.assertIn("penInkPercentEst: 100", text)
        self.assertIn("E_NO_PAPER", text)
        self.assertIn("noPaperMessage", text)

    def test_device_detail_can_request_cancel_and_accept_device_transfer(self):
        text = DEVICE_DETAIL.read_text(encoding="utf-8", errors="replace")
        api = V2_API.read_text(encoding="utf-8", errors="replace")
        types = (ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-mobile" / "src" / "api" / "v2" / "types.ts").read_text(encoding="utf-8", errors="replace")

        self.assertIn("V2DeviceTransferRequest", types)
        self.assertIn("V2DeviceTransferResponse", types)
        self.assertIn("v2RequestDeviceTransfer", api)
        self.assertIn("v2AcceptDeviceTransfer", api)
        self.assertIn("v2CancelDeviceTransfer", api)
        self.assertIn("v2ListPendingIncomingDeviceTransfers", api)
        self.assertIn("`/api/v1/devices/${deviceId}/transfer`", api)
        self.assertIn("`/api/v1/device-transfers/${transferId}/accept`", api)
        self.assertIn("`/api/v1/device-transfers/${transferId}/cancel`", api)
        self.assertIn("'/api/v1/device-transfers/pending-incoming'", api)
        self.assertIn("transferTargetUnionid", text)
        self.assertIn("transferAcceptId", text)
        self.assertIn("deviceTransfer", text)
        self.assertIn("handleRequestTransfer", text)
        self.assertIn("handleCancelTransfer", text)
        self.assertIn("handleAcceptTransfer", text)
        self.assertIn("currentTransferId", text)
        self.assertIn("v2RequestDeviceTransfer(deviceId.value, { targetUnionid })", text)
        self.assertIn("v2CancelDeviceTransfer(transferId)", text)
        self.assertIn("v2AcceptDeviceTransfer(transferId)", text)

    def test_device_detail_primary_can_handle_pending_voice_approvals(self):
        text = DEVICE_DETAIL.read_text(encoding="utf-8", errors="replace")
        api = V2_API.read_text(encoding="utf-8", errors="replace")
        types = (ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-mobile" / "src" / "api" / "v2" / "types.ts").read_text(encoding="utf-8", errors="replace")

        self.assertIn("V2PendingVoiceTaskResponse", types)
        self.assertIn("approvalRequiredBy?: string", types)
        self.assertIn("v2ListPendingVoiceTasks", api)
        self.assertIn("v2ApproveVoiceTask", api)
        self.assertIn("v2RejectVoiceTask", api)
        self.assertIn("`/api/v1/devices/${deviceId}/voice-tasks/pending`", api)
        self.assertIn("`/api/v1/tasks/${taskId}/approve`", api)
        self.assertIn("`/api/v1/tasks/${taskId}/reject`", api)
        self.assertIn("pendingVoiceTasks", text)
        self.assertIn("voiceApprovalLoading", text)
        self.assertIn("pendingVoiceApprovalCount", text)
        self.assertIn("pendingVoiceApprovalBadgeText", text)
        self.assertIn("updateM6PendingTabBarBadge", text)
        self.assertIn("updateM6PendingTabBarBadge('voiceApproval', pendingVoiceTasks.value.length)", text)
        self.assertIn("updateM6PendingTabBarBadge('voiceApproval', 0)", text)
        self.assertIn("loadPendingVoiceTasks", text)
        self.assertIn("v2ListPendingVoiceTasks(deviceId.value)", text)
        self.assertIn("handleApproveVoiceTask", text)
        self.assertIn("handleRejectVoiceTask", text)
        self.assertIn("v2ApproveVoiceTask(taskId", text)
        self.assertIn("v2RejectVoiceTask(taskId", text)
        self.assertIn("pendingVoiceApprovals", text)
        self.assertIn("noPendingVoice", text)
        self.assertIn("approve", text)
        self.assertIn("reject", text)

    def test_device_detail_explains_voiceprint_approval_context(self):
        text = DEVICE_DETAIL.read_text(encoding="utf-8", errors="replace")

        self.assertIn("voiceprintConstraintForTask", text)
        self.assertIn("parseJsonObject(task.constraintsJson)", text)
        self.assertIn("voiceprintApprovalLabel(task)", text)
        self.assertIn("voiceprintReenrollRequired(task)", text)
        self.assertIn("voiceprintHasUnknownSpeaker(task)", text)
        self.assertIn("child_reenroll_required", text)
        self.assertIn("child_unknown_allowed", text)
        self.assertIn("unknown_allowed", text)
        self.assertIn("Child voiceprint re-enroll needed", text)
        self.assertIn("Unknown speaker requires primary review", text)

    def test_device_list_shows_pending_incoming_device_transfers(self):
        text = DEVICE_LIST.read_text(encoding="utf-8", errors="replace")

        self.assertIn("pendingIncomingTransfers", text)
        self.assertIn("pendingIncomingTransferCount", text)
        self.assertIn("pendingIncomingTransferBadgeText", text)
        self.assertIn("updateM6PendingTabBarBadge", text)
        self.assertIn("updateM6PendingTabBarBadge('transfer', pendingIncomingTransfers.value.length)", text)
        self.assertIn("updateM6PendingTabBarBadge('transfer', 0)", text)
        self.assertIn("loadPendingIncomingTransfers", text)
        self.assertIn("v2ListPendingIncomingDeviceTransfers", text)
        self.assertIn("handleAcceptIncomingTransfer", text)
        self.assertIn("v2AcceptDeviceTransfer(transferId)", text)
        self.assertIn("pendingTransfers", text)
        self.assertIn("transfersWaiting", text)

    def test_product_notification_runbook_covers_transfer_and_voice_approval_push_gap(self):
        text = PRODUCT_NOTIFICATIONS_RUNBOOK.read_text(encoding="utf-8", errors="replace")

        for token in (
            "pending incoming device transfers",
            "pending primary voice approvals",
            "in-app count badge",
            "Native tabBar badge support is implemented as a best-effort local badge",
            "reapplies the stored local badge count on app `onShow`",
            "Platform push notifications and background reminders are not implemented",
            "native tabBar rendering on every platform",
            "DeviceTransferService.requestTransfer",
            "AppV2ServiceImpl.submitVoiceTask",
            "pending_primary_approval",
            "/pages/v2/device-list/index",
            "/pages/v2/device-detail/index?deviceId={deviceId}",
            "Do not include child-entered prompt text",
            '"target_ref_type": "device_transfer"',
            '"target_ref_id": "123"',
            "Local Verification",
            "rtk corepack pnpm type-check",
            "best-effort native tabBar badge code",
            "local type safety",
            "WeChat mini-program subscription message template approval",
            "deferral owner, due date, fallback path, and risk acceptance",
            "Release Deferral Record",
            "Use this structure only when platform push or background reminders are intentionally deferred",
            "affected workflow: pending incoming device transfers or pending primary voice approvals",
            "fallback path: pull-based pending lists, in-app count badges, and best-effort native tabBar badge refresh",
            "risk acceptance: product owner and release manager approval reference",
            "rollback trigger: condition that blocks release or disables the deferred workflow",
            "follow-up evidence: template approval, opt-in, send-attempt log, delivery screenshot, deep-link screenshot, and background reminder evidence",
            "background reminder delivery evidence, or an explicit deferral record",
            "Acceptance Drill",
            "does not prove WeChat template approval",
        ):
            self.assertIn(token, text)
        self.assertNotIn('"target_account_id"', text)
        self.assertNotIn("It also does not prove native tabBar rendering", text)
        self.assertNotIn('"capability": "write_text"', text)

    def test_mobile_pending_notification_badge_updates_native_tabbar(self):
        text = UTILS.read_text(encoding="utf-8", errors="replace")
        app = APP_VUE.read_text(encoding="utf-8", errors="replace")

        self.assertIn("updateM6PendingTabBarBadge", text)
        self.assertIn("applyM6PendingTabBarBadge", text)
        self.assertIn("m6_pending_transfer_count", text)
        self.assertIn("m6_pending_voice_approval_count", text)
        self.assertIn("uni.setTabBarBadge", text)
        self.assertIn("uni.removeTabBarBadge", text)
        self.assertIn("M6_PENDING_TABBAR_INDEX = 0", text)
        self.assertIn("total > 99 ? '99+' : String(total)", text)
        self.assertIn("applyM6PendingTabBarBadge", app)
        self.assertIn("onShow", app)
        self.assertLess(app.index("updateTabBarText()"), app.index("applyM6PendingTabBarBadge()"))

    def test_device_detail_can_submit_write_text_task(self):
        text = DEVICE_DETAIL.read_text(encoding="utf-8", errors="replace")

        self.assertIn("handleWriteText", text)
        self.assertIn("writeTextInput", text)
        self.assertIn("defaultWriteTextFontId = 'kai_basic_v1'", text)
        self.assertIn("v2SubmitTask(deviceId.value, 'write_text'", text)
        self.assertIn("font_id: defaultWriteTextFontId", text)
        self.assertIn("write_text:", text)

    def test_device_detail_can_submit_draw_generated_task(self):
        text = DEVICE_DETAIL.read_text(encoding="utf-8", errors="replace")

        self.assertIn("handleDrawPrompt", text)
        self.assertIn("submitDrawGenerated", text)
        self.assertIn("drawPromptInput", text)
        self.assertIn("v2SubmitTask(deviceId.value, 'draw_generated'", text)
        self.assertIn("starterAssets", text)
        for asset_id in ("starter_star", "starter_house", "starter_tree", "starter_fish", "starter_flower"):
            self.assertIn(asset_id, text)
        self.assertIn("starter_id: starterId", text)
        self.assertIn("use_starter_asset: true", text)

    def test_device_detail_can_submit_health_check_and_show_self_check_summary(self):
        text = DEVICE_DETAIL.read_text(encoding="utf-8", errors="replace")

        self.assertIn("healthCheckLoading", text)
        self.assertIn("latestDiagnosticStatus", text)
        self.assertIn("latestDiagnosticSummary", text)
        self.assertIn("selfCheckHistory", text)
        self.assertIn("event?.event_type === 'self_check'", text)
        self.assertIn("applySelfCheck", text)
        self.assertIn("formatSelfCheckSummary", text)
        self.assertIn("loadSelfCheckHistory", text)
        self.assertIn("v2ListSelfCheckHistory(deviceId.value)", text)
        self.assertIn("healthCheckPath", text)
        self.assertIn("v2SubmitTask(deviceId.value, 'run_path'", text)
        self.assertIn("health_check run_path", text)
        for check_name in ("'nvs'", "'wifi'", "'u1_uart'", "'audio'"):
            self.assertIn(check_name, text)

    def test_mobile_api_can_load_self_check_history(self):
        api = V2_API.read_text(encoding="utf-8", errors="replace")
        types = (ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-mobile" / "src" / "api" / "v2" / "types.ts").read_text(encoding="utf-8", errors="replace")

        self.assertIn("V2SelfCheckHistoryResponse", types)
        self.assertIn("checksJson?: string", types)
        self.assertIn("reportedAt?: string", types)
        self.assertIn("v2ListSelfCheckHistory", api)
        self.assertIn("`/api/v1/devices/${deviceId}/self-check/history`", api)

    def test_v2_task_submission_uses_client_request_id(self):
        text = V2_API.read_text(encoding="utf-8", errors="replace")

        self.assertIn("requestId = createTaskRequestId(capability)", text)
        self.assertIn("{ capability, requestId, params, source: 'client' }", text)
        self.assertIn("function createTaskRequestId", text)
        self.assertIn("client-${safeCapability}", text)

    def test_device_config_page_is_registered_and_uses_wifi_fallback_components(self):
        pages = PAGES_JSON.read_text(encoding="utf-8", errors="replace")
        page = DEVICE_CONFIG_PAGE.read_text(encoding="utf-8", errors="replace")

        self.assertIn('"pagePath": "pages/device-config/index"', pages)
        self.assertIn('"path": "pages/device-config/index"', pages)
        self.assertIn("import BlufiConfig", page)
        self.assertIn("import WifiConfig", page)
        self.assertIn("import WifiSelector", page)
        self.assertIn("<blufi-config", page)
        self.assertIn("<wifi-selector", page)
        self.assertIn("<wifi-config", page)
        self.assertIn("configType === 'ble_blufi' || selectedWifiInfo.network", page)
        self.assertIn("configType = ref<'ble_blufi' | 'softap_http' | 'wifi' | 'ultrasonic'>('ble_blufi')", page)
        self.assertIn("value: 'ble_blufi'", page)
        self.assertIn("value: 'softap_http'", page)

    def test_device_config_softap_contract_defines_ap_fallback_endpoints(self):
        text = DEVICE_CONFIG_CONTRACT.read_text(encoding="utf-8", errors="replace")

        for token in (
            "primaryChannel: 'ble_blufi'",
            "fallbackChannel: 'softap_http'",
            "blufiDeviceName: 'Xiaozhi-Blufi'",
            "legacyBlufiDeviceName: 'BLUFI_DEVICE'",
            "blufiServiceUuidCandidates",
            "blufiWriteCharacteristicUuidCandidates",
            "blufiNotifyCharacteristicUuidCandidates",
            "softApBaseUrl: 'http://192.168.4.1'",
            "softApScanPath: '/scan'",
            "softApSubmitPath: '/submit'",
            "softApExitPath: '/exit'",
            "softApSsidHint: 'xiaozhi-XXXXXX'",
            "submitPayloadFields: ['ssid', 'password', 'server_host', 'device_secret']",
            "export function softApUrl",
            "export function matchesBleUuid",
            "export function createDesignTimeBlufiCredentialPayload",
            "Production BluFi must use Espressif protocol frames",
        ):
            self.assertIn(token, text)

    def test_device_config_blufi_client_discovers_connects_and_writes_payload(self):
        text = DEVICE_CONFIG_BLUFI_CONFIG.read_text(encoding="utf-8", errors="replace")

        for token in (
            "openBluetoothAdapter",
            "onBluetoothDeviceFound",
            "startBluetoothDevicesDiscovery",
            "stopBluetoothDevicesDiscovery",
            "createBLEConnection",
            "getBLEDeviceServices",
            "getBLEDeviceCharacteristics",
            "notifyBLECharacteristicValueChange",
            "writeBLECharacteristicValue",
            "provisioningContract.blufiDeviceName",
            "provisioningContract.legacyBlufiDeviceName",
            "provisioningContract.blufiServiceUuidCandidates",
            "provisioningContract.blufiWriteCharacteristicUuidCandidates",
            "provisioningContract.blufiNotifyCharacteristicUuidCandidates",
            "createDesignTimeBlufiCredentialPayload",
            "Scan BLE",
            "Send BLE Config",
        ):
            self.assertIn(token, text)

    def test_device_config_softap_fallback_scans_submits_and_exits(self):
        config = DEVICE_CONFIG_WIFI_CONFIG.read_text(encoding="utf-8", errors="replace")
        selector = DEVICE_CONFIG_WIFI_SELECTOR.read_text(encoding="utf-8", errors="replace")

        self.assertIn("softApUrl(provisioningContract.softApScanPath)", config)
        self.assertIn("softApUrl(provisioningContract.softApSubmitPath)", config)
        self.assertIn("softApUrl(provisioningContract.softApExitPath)", config)
        self.assertIn("ssid: props.selectedNetwork.ssid", config)
        self.assertIn("password: props.selectedNetwork.authmode > 0 ? props.password : ''", config)
        self.assertIn("timeout: 15000", config)
        self.assertIn("toast.success", config)

        self.assertIn("softApUrl(provisioningContract.softApScanPath)", selector)
        self.assertIn("data.success && Array.isArray(data.networks)", selector)
        self.assertIn("data.aps && Array.isArray(data.aps)", selector)
        self.assertIn("Array.isArray(response.data)", selector)
        self.assertIn("connectXiaozhiHotspot", selector)


if __name__ == "__main__":
    unittest.main()
