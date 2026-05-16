import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
API = ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-api"
XIAOZHI = ROOT / "server" / "xiaozhi-esp32-server" / "main" / "xiaozhi-server"

CONTROLLER = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "controller" / "AppV2Controller.java"
SERVICE = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "impl" / "DeviceTransferServiceImpl.java"
SERVICE_TEST = API / "src" / "test" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "DeviceTransferServiceImplTest.java"
GATEWAY = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "impl" / "DeviceServerMotionGatewayImpl.java"
SQL = API / "src" / "main" / "resources" / "db" / "changelog" / "202605160010.sql"
NOTIFICATION_OUTBOX_SQL = API / "src" / "main" / "resources" / "db" / "changelog" / "202605160200.sql"
MASTER = API / "src" / "main" / "resources" / "db" / "changelog" / "db.changelog-master.yaml"
NOTIFICATION_OUTBOX_SERVICE = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "impl" / "ProductNotificationOutboxServiceImpl.java"
DEVICE_TRANSFER_STATUS_DOC = ROOT / "docs" / "M6.5-device-transfer-status.md"
VOICEPRINT_CACHE = XIAOZHI / "core" / "utils" / "voiceprint_cache.py"
HTTP_SERVER = XIAOZHI / "core" / "http_server.py"
VOICEPRINT_HANDLER = XIAOZHI / "core" / "api" / "voiceprint_cache_handler.py"


class ManagerApiDeviceTransferTest(unittest.TestCase):
    def test_transfer_endpoints_exist(self):
        text = CONTROLLER.read_text(encoding="utf-8", errors="replace")

        self.assertIn('"/devices/{deviceId}/transfer"', text)
        self.assertIn('"/device-transfers/{transferId}/accept"', text)
        self.assertIn('"/device-transfers/{transferId}/cancel"', text)
        self.assertIn('"/device-transfers/pending-incoming"', text)
        self.assertIn("DeviceTransferService", text)

    def test_transfer_table_is_registered(self):
        sql = SQL.read_text(encoding="utf-8", errors="replace")
        master = MASTER.read_text(encoding="utf-8", errors="replace")

        self.assertIn("CREATE TABLE IF NOT EXISTS `device_transfer_requests`", sql)
        self.assertIn("source_account_id", sql)
        self.assertIn("target_account_id", sql)
        self.assertIn("pending|accepted|cancelled", sql)
        self.assertIn("202605160010.sql", master)

    def test_transfer_accept_keeps_history_and_clears_sensitive_state(self):
        text = SERVICE.read_text(encoding="utf-8", errors="replace")
        service_test = SERVICE_TEST.read_text(encoding="utf-8", errors="replace")
        ensure_binding = text[text.index("private V2DeviceBindingEntity ensureActiveBinding"):]

        self.assertIn("BINDING_STATUS_TRANSFERRED", text)
        self.assertIn("STATUS_CANCELLED", text)
        self.assertIn("cancelTransfer", text)
        self.assertIn("listPendingIncomingTransfers", text)
        self.assertIn("ProductNotificationOutboxService", text)
        self.assertIn("enqueuePendingDeviceTransfer", text)
        self.assertIn("getTargetAccountId, user.getId()", text)
        self.assertIn("v2AccountDao.selectById(user.getId())", text)
        self.assertIn("throw new RenException(ErrorCode.ACCOUNT_DISABLE)", text)
        self.assertIn("requestTransferRejectsDeletedAccountTombstoneBeforeBindingLookup", service_test)
        self.assertIn("never()).selectOne", service_test)
        self.assertIn("never()).insert", service_test)
        self.assertIn("orderByDesc", text)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getUpdatedAt)", ensure_binding)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getId)", ensure_binding)
        self.assertIn("getSourceAccountId", text)
        self.assertIn("targetBinding.setAccountId(user.getId())", text)
        self.assertIn("v2MemberDao.update", text)
        self.assertIn("voiceprint.setStatus(STATUS_DELETED)", text)
        self.assertIn("voiceprint.setEmbeddingHash(\"0\".repeat(64))", text)
        self.assertIn("deviceServerMotionGateway.clearVoiceprintCache", text)
        accept_method = text[text.index("public V2DeviceTransferResponse acceptTransfer"):]
        accept_method = accept_method[:accept_method.index("public V2DeviceTransferResponse cancelTransfer")]
        cancel_method = text[text.index("public V2DeviceTransferResponse cancelTransfer"):]
        cancel_method = cancel_method[:cancel_method.index("public List<V2DeviceTransferResponse> listPendingIncomingTransfers")]
        self.assertIn("productNotificationOutboxService.resolveDeviceTransfer(transfer.getId())", accept_method)
        self.assertIn("productNotificationOutboxService.cancelDeviceTransfer(transfer.getId())", cancel_method)
        self.assertLess(
            accept_method.index("v2DeviceTransferRequestDao.updateById(transfer)"),
            accept_method.index("productNotificationOutboxService.resolveDeviceTransfer(transfer.getId())"),
        )
        self.assertLess(
            cancel_method.index("v2DeviceTransferRequestDao.updateById(transfer)"),
            cancel_method.index("productNotificationOutboxService.cancelDeviceTransfer(transfer.getId())"),
        )
        self.assertNotIn("V2TaskDao", text)

    def test_device_server_clear_cache_contract_exists(self):
        cache = VOICEPRINT_CACHE.read_text(encoding="utf-8", errors="replace")
        handler = VOICEPRINT_HANDLER.read_text(encoding="utf-8", errors="replace")
        http = HTTP_SERVER.read_text(encoding="utf-8", errors="replace")
        gateway = GATEWAY.read_text(encoding="utf-8", errors="replace")

        self.assertIn("def clear_device", cache)
        self.assertIn("VoiceprintCacheHandler", handler)
        self.assertIn("auth_error = self._authorize(request)", handler)
        self.assertIn("internal_motion_task_token", handler)
        self.assertIn("missing bearer token", handler)
        self.assertIn("invalid token", handler)
        self.assertIn('"/internal/v1/voiceprints/cache/clear"', http)
        self.assertIn("clearVoiceprintCache", gateway)
        self.assertIn("headers.setBearerAuth(token)", gateway)

    def test_pending_transfer_notification_outbox_is_safe_and_registered(self):
        sql = NOTIFICATION_OUTBOX_SQL.read_text(encoding="utf-8", errors="replace")
        master = MASTER.read_text(encoding="utf-8", errors="replace")
        service = NOTIFICATION_OUTBOX_SERVICE.read_text(encoding="utf-8", errors="replace")

        self.assertIn("CREATE TABLE IF NOT EXISTS `product_notification_events`", sql)
        self.assertIn("pending|sent|failed|resolved|cancelled", sql)
        self.assertIn("idx_product_notification_recipient_status", sql)
        self.assertIn("202605160200.sql", master)
        self.assertIn("EVENT_PENDING_DEVICE_TRANSFER", service)
        self.assertIn("buildSafeProviderPayload", service)
        self.assertIn("markProviderResult(eventId, STATUS_SENT, true)", service)
        self.assertIn("markProviderResult(eventId, STATUS_FAILED, false)", service)
        self.assertIn(".eq(\"id\", eventId)", service)
        self.assertIn("putIfNotBlank(payload, \"event\"", service)
        self.assertIn("putIfNotBlank(payload, \"device_id\"", service)
        self.assertIn("putIfNotBlank(payload, \"target_ref_type\"", service)
        self.assertIn("putIfNotBlank(payload, \"target_ref_id\"", service)
        self.assertIn("putIfNotBlank(payload, \"deep_link\"", service)
        self.assertIn("/pages/v2/device-list/index", service)
        self.assertIn("resolveDeviceTransfer", service)
        self.assertIn("cancelDeviceTransfer", service)
        self.assertIn(".eq(\"status\", STATUS_PENDING)", service)
        self.assertNotIn("activationCode", service)

    def test_device_transfer_status_doc_documents_outbox_payload_minimization(self):
        text = DEVICE_TRANSFER_STATUS_DOC.read_text(encoding="utf-8", errors="replace")

        self.assertIn("receives only recipient, device, and transfer references", text)
        self.assertIn("does not accept activation codes, account secrets, or other binding payload fields", text)
        self.assertIn("ProductNotificationOutboxService.buildSafeProviderPayload", text)
        self.assertIn("event`, `device_id`, `target_ref_type`, `target_ref_id`, and `deep_link", text)
        self.assertIn("does not expose recipient account id, outbox status, activation codes", text)
        self.assertIn("ProductNotificationOutboxService.markProviderSent", text)
        self.assertIn("markProviderFailed", text)
        self.assertIn("update only still-pending outbox rows by id", text)
        self.assertIn("safe provider payload builder exist for future push workers", text)
        self.assertIn("reapplies the stored local badge count on app `onShow`", text)
        self.assertIn("release ticket must record the deferral owner, due date, fallback path, and risk acceptance", text)
        self.assertIn("transfer request and acceptance binding ownership checks select active bindings by `updated_at` desc and binding `id` desc", text)
        self.assertIn("Voiceprint enrollment also selects account/device active bindings by `updated_at` desc and binding `id` desc", text)
        self.assertIn("voiceprint cache export first resolves the latest active binding", text)
        self.assertIn("returns only active voiceprints for that binding account and device", text)
        self.assertIn("If no active binding exists, the exported cache is empty", text)


if __name__ == "__main__":
    unittest.main()
