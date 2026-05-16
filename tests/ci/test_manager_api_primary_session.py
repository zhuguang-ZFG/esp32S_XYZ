import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
API = ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-api"
APP_SERVICE = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "impl" / "AppV2ServiceImpl.java"
APP_SERVICE_INTERFACE = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "AppV2Service.java"
APP_CONTROLLER = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "controller" / "AppV2Controller.java"
PRIMARY_SERVICE = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "impl" / "PrimarySessionServiceImpl.java"
ACCOUNT_ENTITY = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "entity" / "V2AccountEntity.java"
WS_HANDLER = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "ws" / "ClientEdgeWebSocketHandler.java"
EXCEPTION_HANDLER = API / "src" / "main" / "java" / "xiaozhi" / "common" / "exception" / "RenExceptionHandler.java"
PRIMARY_TEST = API / "src" / "test" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "PrimarySessionServiceImplTest.java"
WS_TEST = API / "src" / "test" / "java" / "xiaozhi" / "modules" / "appv2" / "ws" / "ClientEdgeWebSocketHandlerTest.java"
PRIMARY_STATUS_DOC = ROOT / "docs" / "M6.4-primary-session-status.md"
CHANGELOG_MASTER = API / "src" / "main" / "resources" / "db" / "changelog" / "db.changelog-master.yaml"
LEASE_MIGRATION = API / "src" / "main" / "resources" / "db" / "changelog" / "202605160100.sql"
APP_SERVICE_TEST = API / "src" / "test" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "AppV2ServiceImplTest.java"
APP_CONTROLLER_TEST = API / "src" / "test" / "java" / "xiaozhi" / "modules" / "appv2" / "controller" / "AppV2ControllerTest.java"
APPROVAL_MIGRATION = API / "src" / "main" / "resources" / "db" / "changelog" / "202605160130.sql"
NOTIFICATION_OUTBOX_MIGRATION = API / "src" / "main" / "resources" / "db" / "changelog" / "202605160200.sql"
NOTIFICATION_OUTBOX_INTERFACE = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "ProductNotificationOutboxService.java"
NOTIFICATION_OUTBOX_SERVICE = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "impl" / "ProductNotificationOutboxServiceImpl.java"
PRODUCT_NOTIFICATIONS_RUNBOOK = ROOT / "ops" / "runbooks" / "m6-product-notifications.md"


class ManagerApiPrimarySessionTest(unittest.TestCase):
    def test_rest_submit_task_requires_current_primary_session_before_write(self):
        text = APP_SERVICE.read_text(encoding="utf-8", errors="replace")

        self.assertIn("PrimarySessionService", text)
        self.assertIn("primarySessionService.requirePrimaryForWrite", text)
        self.assertIn("SecurityUser.getToken()", text)
        self.assertLess(
            text.index("primarySessionService.requirePrimaryForWrite"),
            text.index("return submitTaskForAccount"),
        )

    def test_voice_submit_task_waits_for_primary_approval_when_primary_blocks_voice(self):
        text = APP_SERVICE.read_text(encoding="utf-8", errors="replace")
        voice_method = text[text.index("public V2SubmitTaskResponse submitVoiceTask"):]

        self.assertIn("primarySessionService.requireVoiceAllowedForWrite", voice_method)
        self.assertIn("submitPendingVoiceTaskForPrimaryApproval", voice_method)
        self.assertIn("TASK_STATUS_PENDING_PRIMARY_APPROVAL", text)
        self.assertIn('"pending_primary_approval"', text)
        self.assertIn("ProductNotificationOutboxService", text)
        self.assertIn("enqueuePendingPrimaryVoiceApproval", text)
        self.assertIn('"primary"', text)
        self.assertIn("activeBindingByDevice(normalizedDeviceId)", voice_method)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getUpdatedAt)", text)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getId)", text)

    def test_primary_can_approve_or_reject_individual_voice_tasks(self):
        service = APP_SERVICE.read_text(encoding="utf-8", errors="replace")
        interface = APP_SERVICE_INTERFACE.read_text(encoding="utf-8", errors="replace")
        controller = APP_CONTROLLER.read_text(encoding="utf-8", errors="replace")

        self.assertIn("approveVoiceTask(String taskId", interface)
        self.assertIn("rejectVoiceTask(String taskId", interface)
        self.assertIn("public V2SubmitTaskResponse approveVoiceTask", service)
        self.assertIn("public V2SubmitTaskResponse rejectVoiceTask", service)
        self.assertIn("requirePendingVoiceTask", service)
        self.assertIn("primarySessionService.requirePrimaryForWrite", service)
        self.assertIn("forwardAcceptedTask", service)
        self.assertIn("E_PRIMARY_REJECTED", service)
        approve_method = service[service.index("public V2SubmitTaskResponse approveVoiceTask"):]
        approve_method = approve_method[:approve_method.index("public V2SubmitTaskResponse rejectVoiceTask")]
        reject_method = service[service.index("public V2SubmitTaskResponse rejectVoiceTask"):]
        reject_method = reject_method[:reject_method.index("public List<V2PendingVoiceTaskResponse> listPendingVoiceTasks")]
        self.assertIn("productNotificationOutboxService.resolvePrimaryVoiceApproval(task.getId())", approve_method)
        self.assertIn("productNotificationOutboxService.cancelPrimaryVoiceApproval(task.getId())", reject_method)
        self.assertLess(
            approve_method.index("v2TaskDao.updateById(task)"),
            approve_method.index("productNotificationOutboxService.resolvePrimaryVoiceApproval(task.getId())"),
        )
        self.assertLess(
            reject_method.index("v2TaskDao.updateById(task)"),
            reject_method.index("productNotificationOutboxService.cancelPrimaryVoiceApproval(task.getId())"),
        )
        self.assertIn('"/tasks/{taskId}/approve"', controller)
        self.assertIn('"/tasks/{taskId}/reject"', controller)

    def test_primary_can_list_pending_voice_tasks_for_mobile_review(self):
        service = APP_SERVICE.read_text(encoding="utf-8", errors="replace")
        interface = APP_SERVICE_INTERFACE.read_text(encoding="utf-8", errors="replace")
        controller = APP_CONTROLLER.read_text(encoding="utf-8", errors="replace")
        app_service_test = APP_SERVICE_TEST.read_text(encoding="utf-8", errors="replace")
        app_controller_test = APP_CONTROLLER_TEST.read_text(encoding="utf-8", errors="replace")

        self.assertIn("V2PendingVoiceTaskResponse", interface)
        self.assertIn("List<V2PendingVoiceTaskResponse> listPendingVoiceTasks", interface)
        self.assertIn("public List<V2PendingVoiceTaskResponse> listPendingVoiceTasks", service)
        self.assertIn("ensureActiveBinding(user.getId(), normalizedDeviceId)", service)
        self.assertIn(".eq(V2TaskEntity::getSource, TASK_SOURCE_VOICE)", service)
        self.assertIn(".eq(V2TaskEntity::getStatus, TASK_STATUS_PENDING_PRIMARY_APPROVAL)", service)
        self.assertIn('"/devices/{deviceId}/voice-tasks/pending"', controller)
        self.assertIn("listPendingVoiceTasksReturnsCurrentAccountDevicePendingVoiceTasks", app_service_test)
        self.assertIn("listPendingVoiceTasksDelegatesToAppService", app_controller_test)

    def test_primary_session_service_uses_active_binding_and_e_not_primary(self):
        text = PRIMARY_SERVICE.read_text(encoding="utf-8", errors="replace")
        require_binding = text[text.index("private V2DeviceBindingEntity requireActiveBinding"):]

        self.assertIn('ERROR_NOT_PRIMARY = "E_NOT_PRIMARY"', text)
        self.assertIn("requireActiveBinding(accountId, deviceId)", text)
        self.assertIn("V2DeviceBindingEntity::getBindingStatus", text)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getUpdatedAt)", require_binding)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getId)", require_binding)
        self.assertIn("account.setPrimarySessionId(normalizedSessionId)", text)
        self.assertIn("account.setPrimarySessionClaimedAt(now)", text)
        self.assertIn("PRIMARY_SESSION_LEASE_MS = 60_000L", text)
        self.assertIn("releaseExpiredPrimarySession(account)", text)
        self.assertIn("account.setPrimarySessionClaimedAt(null)", text)
        self.assertIn("requireVoiceAllowedForWrite", text)
        self.assertIn("primary session blocks voice write", text)

    def test_primary_session_lease_timestamp_is_migrated(self):
        entity = ACCOUNT_ENTITY.read_text(encoding="utf-8", errors="replace")
        master = CHANGELOG_MASTER.read_text(encoding="utf-8", errors="replace")
        migration = LEASE_MIGRATION.read_text(encoding="utf-8", errors="replace")

        self.assertIn("primarySessionClaimedAt", entity)
        self.assertIn("202605160100.sql", master)
        self.assertIn("primary_session_claimed_at", migration)
        self.assertIn("AFTER `primary_session_id`", migration)

    def test_task_status_width_supports_pending_primary_approval(self):
        master = CHANGELOG_MASTER.read_text(encoding="utf-8", errors="replace")
        migration = APPROVAL_MIGRATION.read_text(encoding="utf-8", errors="replace")

        self.assertIn("202605160130.sql", master)
        self.assertIn("MODIFY COLUMN `status` VARCHAR(32)", migration)
        self.assertIn("pending_primary_approval", migration)

    def test_pending_voice_approval_notification_outbox_is_safe_and_registered(self):
        migration = NOTIFICATION_OUTBOX_MIGRATION.read_text(encoding="utf-8", errors="replace")
        master = CHANGELOG_MASTER.read_text(encoding="utf-8", errors="replace")
        interface = NOTIFICATION_OUTBOX_INTERFACE.read_text(encoding="utf-8", errors="replace")
        service = NOTIFICATION_OUTBOX_SERVICE.read_text(encoding="utf-8", errors="replace")

        self.assertIn("CREATE TABLE IF NOT EXISTS `product_notification_events`", migration)
        self.assertIn("recipient_account_id", migration)
        self.assertIn("deep_link", migration)
        self.assertIn("202605160200.sql", master)
        self.assertIn("pending|sent|failed|resolved|cancelled", migration)
        self.assertIn(
            "enqueuePendingPrimaryVoiceApproval(Long recipientAccountId, String deviceId, String taskId)",
            interface,
        )
        self.assertNotIn("String capability", interface)
        self.assertIn("EVENT_PENDING_PRIMARY_VOICE_APPROVAL", service)
        self.assertIn("buildSafeProviderPayload", interface)
        self.assertIn("markProviderSent(Long eventId)", interface)
        self.assertIn("markProviderFailed(Long eventId)", interface)
        self.assertIn("buildSafeProviderPayload", service)
        self.assertIn("markProviderResult(eventId, STATUS_SENT, true)", service)
        self.assertIn("markProviderResult(eventId, STATUS_FAILED, false)", service)
        self.assertIn(".eq(\"id\", eventId)", service)
        self.assertIn("putIfNotBlank(payload, \"event\"", service)
        self.assertIn("putIfNotBlank(payload, \"device_id\"", service)
        self.assertIn("putIfNotBlank(payload, \"target_ref_type\"", service)
        self.assertIn("putIfNotBlank(payload, \"target_ref_id\"", service)
        self.assertIn("putIfNotBlank(payload, \"deep_link\"", service)
        self.assertIn("/pages/v2/device-detail/index?deviceId=", service)
        self.assertIn("UriUtils.encodeQueryParam", service)
        self.assertIn("StandardCharsets.UTF_8", service)
        self.assertIn("resolvePrimaryVoiceApproval", service)
        self.assertIn("cancelPrimaryVoiceApproval", service)
        self.assertIn(".eq(\"status\", STATUS_PENDING)", service)
        self.assertNotIn("transcript", service.lower())
        self.assertNotIn("prompt", service.lower())

    def test_primary_status_doc_documents_outbox_payload_minimization(self):
        text = PRIMARY_STATUS_DOC.read_text(encoding="utf-8", errors="replace")

        self.assertIn("receives only recipient, device, and task references", text)
        self.assertIn("does not accept capability, prompt, transcript, or other task payload fields", text)
        self.assertIn("ProductNotificationOutboxService.buildSafeProviderPayload", text)
        self.assertIn("event`, `device_id`, `target_ref_type`, `target_ref_id`, and `deep_link", text)
        self.assertIn("does not expose recipient account id, outbox status, capability, prompt, transcript", text)
        self.assertIn("ProductNotificationOutboxService.markProviderSent", text)
        self.assertIn("markProviderFailed", text)
        self.assertIn("update only still-pending outbox rows by id", text)
        self.assertIn("safe provider payload builder exist for future push workers", text)
        self.assertIn("reapplies the stored local badge count on app `onShow`", text)
        self.assertIn("release ticket must record the deferral owner, due date, fallback path, and risk acceptance", text)
        self.assertIn("selecting the latest active binding ordered by `updated_at` desc", text)
        self.assertIn("client write, primary claim, pending-approval listing, and Edge-A device subscription checks", text)
        self.assertIn("then binding `id` desc", text)

    def test_product_notification_runbook_documents_safe_payload_contract(self):
        text = PRODUCT_NOTIFICATIONS_RUNBOOK.read_text(encoding="utf-8", errors="replace")

        self.assertIn("using only safe semantic fields and deep links", text)
        self.assertIn("Do not include child-entered prompt text", text)
        self.assertIn("raw voice transcript", text)
        self.assertIn("biometric material", text)
        self.assertIn("activation codes", text)
        self.assertIn("The local outbox intentionally stores only:", text)
        self.assertIn("Future Push Worker Payload Contract", text)
        self.assertIn("ProductNotificationOutboxService.buildSafeProviderPayload", text)
        self.assertIn("ProductNotificationOutboxService.markProviderSent", text)
        self.assertIn("ProductNotificationOutboxService.markProviderFailed", text)
        self.assertIn("It may expose only:", text)
        self.assertIn('"target_ref_type": "task"', text)
        self.assertIn('"target_ref_id": "task-..."', text)
        self.assertIn("read only `pending` rows", text)
        self.assertIn("leave resolved/cancelled rows untouched", text)
        self.assertIn("Local Verification", text)
        self.assertIn("rtk python -m unittest tests.ci.test_manager_api_primary_session tests.ci.test_manager_api_device_transfer tests.ci.test_manager_mobile_device_info -v", text)
        self.assertIn("rtk mvn \"-Dtest=AppV2ServiceImplTest,DeviceTransferServiceImplTest,ProductNotificationOutboxServiceImplTest\" test", text)
        self.assertIn("It must not expose `recipient_account_id`, outbox `status`", text)
        self.assertNotIn('"target_account_id"', text)
        self.assertNotIn('"task_id": "task-..."', text)
        self.assertNotIn('"created_at"', text)
        self.assertIn("This method does not send platform notifications", text)
        self.assertIn("Native tabBar badge support is implemented as a best-effort local badge", text)
        self.assertIn("Platform push notifications and background reminders are not implemented", text)
        self.assertIn("deferral owner, due date, fallback path, and risk acceptance", text)
        self.assertIn("does not prove WeChat template approval, platform push delivery, native tabBar rendering on every platform, or background reminder delivery", text)
        self.assertNotIn('"capability"', text)

    def test_edge_a_claim_primary_reuses_authenticated_session_token(self):
        text = WS_HANDLER.read_text(encoding="utf-8", errors="replace")
        subscribe_method = text[text.index("private void handleSubscribeDevice"):]
        subscribe_method = subscribe_method[:subscribe_method.index("private void handleSubscribeTask")]

        self.assertIn('ATTR_SESSION_ID = "edgeA.sessionId"', text)
        self.assertIn("revalidateAuthenticatedSession", text)
        self.assertIn("sysUserTokenService.getUserByToken(sessionId)", text)
        self.assertIn("V2AccountDao", text)
        self.assertIn("ACCOUNT_STATUS_DELETED", text)
        self.assertIn("isDeletedAccount", text)
        self.assertIn('case "claim_primary"', text)
        self.assertIn("primarySessionService.claimPrimary", text)
        self.assertIn('"primary_claimed"', text)
        self.assertIn('"role"', text)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getUpdatedAt)", subscribe_method)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getId)", subscribe_method)
        ws_test = WS_TEST.read_text(encoding="utf-8", errors="replace")
        self.assertIn("subscribeDeviceRevalidatesTokenBeforeUsingAuthenticatedSession", ws_test)
        self.assertIn("authRejectsDeletedAccountTombstone", ws_test)
        self.assertIn("verify(bindingDao, never()).selectOne", ws_test)

    def test_app_v2_client_write_binding_checks_are_deterministic(self):
        text = APP_SERVICE.read_text(encoding="utf-8", errors="replace")
        ensure_binding = text[text.index("private void ensureActiveBinding"):]
        ensure_binding = ensure_binding[:ensure_binding.index("private V2DeviceBindingEntity activeBindingByDevice")]

        self.assertIn("V2DeviceBindingEntity::getAccountId", ensure_binding)
        self.assertIn("V2DeviceBindingEntity::getDeviceId", ensure_binding)
        self.assertIn("V2DeviceBindingEntity::getBindingStatus", ensure_binding)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getUpdatedAt)", ensure_binding)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getId)", ensure_binding)

    def test_e_not_primary_is_exposed_to_clients(self):
        text = EXCEPTION_HANDLER.read_text(encoding="utf-8", errors="replace")

        self.assertIn("handlePrimarySessionException", text)
        self.assertIn("PrimarySessionException", text)
        self.assertIn("ex.getCode() + \": \" + ex.getMessage()", text)

    def test_primary_session_has_java_drills(self):
        primary_test = PRIMARY_TEST.read_text(encoding="utf-8", errors="replace")
        ws_test = WS_TEST.read_text(encoding="utf-8", errors="replace")

        self.assertIn("requirePrimaryForWriteAllowsMatchingPrimarySession", primary_test)
        self.assertIn("requirePrimaryForWriteRejectsNonPrimarySessionWithENotPrimary", primary_test)
        self.assertIn("requireVoiceAllowedForWriteRejectsWhenPrimarySessionClaimed", primary_test)
        self.assertIn("claimPrimaryStoresCurrentSession", primary_test)
        self.assertIn("requirePrimaryForWriteReleasesExpiredPrimaryLease", primary_test)
        self.assertIn("requireVoiceAllowedForWriteAllowsAfterExpiredPrimaryLeaseIsReleased", primary_test)
        self.assertIn("claimPrimaryDelegatesAndAcknowledgesCurrentSession", ws_test)
        app_service_test = APP_SERVICE_TEST.read_text(encoding="utf-8", errors="replace")
        app_controller_test = APP_CONTROLLER_TEST.read_text(encoding="utf-8", errors="replace")
        self.assertIn("submitVoiceTaskCreatesPendingApprovalWhenPrimarySessionBlocksVoiceWithoutForwarding", app_service_test)
        self.assertIn("approveVoiceTaskRequiresPrimaryAndForwardsPendingVoiceTask", app_service_test)
        self.assertIn("rejectVoiceTaskRequiresPrimaryAndMarksTaskRejectedWithoutForwarding", app_service_test)
        self.assertIn("voiceTaskApprovalEndpointsDelegateToAppService", app_controller_test)


if __name__ == "__main__":
    unittest.main()
