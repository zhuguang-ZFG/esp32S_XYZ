import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
API = ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-api"

CONTROLLER = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "controller" / "AppV2Controller.java"
INTERNAL_CONTROLLER = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "controller" / "InternalMotionEventController.java"
CONFIG_CONTROLLER = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "config" / "controller" / "ConfigController.java"
CONFIG_SERVICE = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "config" / "service" / "impl" / "ConfigServiceImpl.java"
SERVICE = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "impl" / "DeviceRmaServiceImpl.java"
SQL = API / "src" / "main" / "resources" / "db" / "changelog" / "202605160040.sql"
RMA_EVENT_SQL = API / "src" / "main" / "resources" / "db" / "changelog" / "202605160220.sql"
MASTER = API / "src" / "main" / "resources" / "db" / "changelog" / "db.changelog-master.yaml"
RMA_REQUEST = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "dto" / "V2DeviceRmaRequest.java"
RMA_EVENT_ENTITY = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "entity" / "V2DeviceRmaEventEntity.java"
RMA_EVENT_DAO = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "dao" / "V2DeviceRmaEventDao.java"
INTERNAL_CONTROLLER_TEST = API / "src" / "test" / "java" / "xiaozhi" / "modules" / "appv2" / "controller" / "InternalMotionEventControllerTest.java"
CONFIG_CONTROLLER_TEST = API / "src" / "test" / "java" / "xiaozhi" / "modules" / "config" / "controller" / "ConfigControllerTest.java"
RMA_SERVICE_TEST = API / "src" / "test" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "DeviceRmaServiceImplTest.java"
APP_CONTROLLER_TEST = API / "src" / "test" / "java" / "xiaozhi" / "modules" / "appv2" / "controller" / "AppV2ControllerTest.java"
RMA_STATUS_DOC = ROOT / "docs" / "M6.7-device-rma-status.md"
RMA_RUNBOOK = ROOT / "ops" / "runbooks" / "m6-device-rma.md"


class ManagerApiDeviceRmaTest(unittest.TestCase):
    def test_rma_endpoints_exist(self):
        text = CONTROLLER.read_text(encoding="utf-8", errors="replace")

        self.assertIn("PERMISSION_SUPER_ADMIN", text)
        self.assertIn('"sys:role:superAdmin"', text)
        self.assertIn("PERMISSION_RMA_OPERATOR", text)
        self.assertIn('"appv2:device:rma"', text)
        self.assertIn("logical = Logical.OR", text)
        self.assertIn('"/devices/{deviceId}/rma/start"', text)
        self.assertIn('"/devices/{deviceId}/rma/complete"', text)
        self.assertIn('"/devices/{deviceId}/return"', text)
        self.assertIn('"/devices/{deviceId}/restock"', text)
        self.assertIn('"/devices/{deviceId}/dispose"', text)
        self.assertIn('"/devices/{deviceId}/rma/events"', text)
        self.assertIn("listDeviceRmaEvents", text)
        self.assertIn("DeviceRmaService", text)
        self.assertIn("deviceRmaEndpointsAllowSuperAdminOrRmaOperatorPermission", APP_CONTROLLER_TEST.read_text(encoding="utf-8", errors="replace"))

    def test_status_columns_are_wide_enough_for_rma_states(self):
        sql = SQL.read_text(encoding="utf-8", errors="replace")
        master = MASTER.read_text(encoding="utf-8", errors="replace")

        self.assertIn("MODIFY COLUMN `status` VARCHAR(32)", sql)
        self.assertIn("MODIFY COLUMN `binding_status` VARCHAR(32)", sql)
        self.assertIn("rma_in_progress", sql)
        self.assertIn("provisioned_unbound", sql)
        self.assertIn("202605160040.sql", master)

    def test_rma_operator_audit_events_are_registered(self):
        sql = RMA_EVENT_SQL.read_text(encoding="utf-8", errors="replace")
        master = MASTER.read_text(encoding="utf-8", errors="replace")
        entity = RMA_EVENT_ENTITY.read_text(encoding="utf-8", errors="replace")
        request = RMA_REQUEST.read_text(encoding="utf-8", errors="replace")
        dao = RMA_EVENT_DAO.read_text(encoding="utf-8", errors="replace")
        service = SERVICE.read_text(encoding="utf-8", errors="replace")
        service_test = RMA_SERVICE_TEST.read_text(encoding="utf-8", errors="replace")

        for token in (
            "CREATE TABLE IF NOT EXISTS `device_rma_events`",
            "operator_account_id",
            "from_device_status",
            "to_device_status",
            "from_binding_status",
            "to_binding_status",
            "factory_cleaned",
            "evidence_ref",
            "ticket_ref",
            "idx_device_rma_events_operator_time",
            "idx_device_rma_events_ticket_time",
        ):
            self.assertIn(token, sql)
        self.assertIn("202605160220.sql", master)
        self.assertIn("private Boolean factoryCleaned", request)
        self.assertIn("private String evidenceRef", request)
        self.assertIn("private String ticketRef", request)
        self.assertIn('@TableName("device_rma_events")', entity)
        self.assertIn("private Long operatorAccountId", entity)
        self.assertIn("private Boolean factoryCleaned", entity)
        self.assertIn("private String evidenceRef", entity)
        self.assertIn("private String ticketRef", entity)
        self.assertIn("BaseMapper<V2DeviceRmaEventEntity>", dao)
        self.assertIn("V2DeviceRmaEventDao", service)
        self.assertIn("recordRmaEvent", service)
        self.assertIn("listAuditEvents", service)
        self.assertIn("requireFactoryCleaningEvidence(request)", service)
        self.assertIn("Boolean.TRUE.equals(request.getFactoryCleaned())", service)
        self.assertIn("StringUtils.isBlank(request.getEvidenceRef())", service)
        self.assertIn("event.setOperatorAccountId(user.getId())", service)
        self.assertIn("event.setFactoryCleaned", service)
        self.assertIn("event.setEvidenceRef", service)
        self.assertIn("event.setTicketRef", service)
        self.assertIn("repair_start", service)
        self.assertIn("repair_complete", service)
        self.assertIn("return_confirm", service)
        self.assertIn("restock", service)
        self.assertIn("dispose", service)
        self.assertIn("V2DeviceRmaEventEntity", service_test)
        self.assertIn("verify(v2DeviceRmaEventDao, times(2)).insert", service_test)
        self.assertIn("restockAndDisposeRequireFactoryCleaningEvidence", service_test)
        self.assertIn("listAuditEventsReturnsRecentDeviceEventsForEvidenceExport", service_test)

    def test_rma_service_covers_repair_return_restock_and_dispose_without_tasks(self):
        text = SERVICE.read_text(encoding="utf-8", errors="replace")
        service_test = RMA_SERVICE_TEST.read_text(encoding="utf-8", errors="replace")
        require_binding = text[text.index("private V2DeviceBindingEntity requireBinding"):]
        require_binding = require_binding[:require_binding.index("private V2DeviceBindingEntity latestKnownBinding")]
        update_activation = text[text.index("private void updateActivationStatus"):]

        self.assertIn("DEVICE_RMA_IN_PROGRESS", text)
        self.assertIn("DEVICE_RETURNED", text)
        self.assertIn("DEVICE_PROVISIONED_UNBOUND", text)
        self.assertIn("DEVICE_DISPOSED", text)
        self.assertIn("BINDING_RMA_IN_PROGRESS", text)
        self.assertIn("BINDING_RETURNED", text)
        self.assertIn("ACTIVATION_REVOKED", text)
        self.assertIn("ACTIVATION_PROVISIONED", text)
        self.assertIn("requireBinding(String deviceId, String status)", text)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getUpdatedAt)", require_binding)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getId)", require_binding)
        self.assertIn("latestKnownBinding", text)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getUpdatedAt)", text)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getId)", text)
        self.assertIn("orderByDesc(V2ActivationCodeEntity::getUpdatedAt)", update_activation)
        self.assertIn("orderByDesc(V2ActivationCodeEntity::getId)", update_activation)
        self.assertNotIn("eq(V2DeviceBindingEntity::getAccountId", text)
        self.assertNotIn("V2TaskDao", text)
        self.assertIn("restockReturnedGeneratesFreshActivationWhenNoReplacementCodeProvided", service_test)

    def test_disposed_device_is_rejected_from_runtime_internal_entrypoints(self):
        controller = INTERNAL_CONTROLLER.read_text(encoding="utf-8", errors="replace")
        controller_test = INTERNAL_CONTROLLER_TEST.read_text(encoding="utf-8", errors="replace")

        self.assertIn("V2DeviceDao", controller)
        self.assertIn("requireDeviceNotDisposed(body)", controller)
        self.assertIn('"disposed".equals(device.getStatus())', controller)
        self.assertIn("E_DEVICE_DISPOSED: device is disposed", controller)
        self.assertIn("rejectsDisposedDeviceBeforeForwardingRuntimeEvents", controller_test)
        self.assertIn("rejectsDisposedDeviceBeforeSubmittingVoiceTask", controller_test)

    def test_device_runtime_status_endpoint_supports_websocket_auth_refusal(self):
        controller = CONFIG_CONTROLLER.read_text(encoding="utf-8", errors="replace")
        service = CONFIG_SERVICE.read_text(encoding="utf-8", errors="replace")
        controller_test = CONFIG_CONTROLLER_TEST.read_text(encoding="utf-8", errors="replace")

        self.assertIn('PostMapping("device-runtime-status")', controller)
        self.assertIn("DeviceRuntimeStatusDTO", controller)
        self.assertIn("DeviceRuntimeStatusVO", controller)
        self.assertIn("V2DeviceDao", service)
        self.assertIn("v2DeviceDao.selectById", service)
        self.assertIn("V2DeviceEntity::getDeviceSn", service)
        self.assertIn('"disposed".equals(status)', service)
        self.assertIn("deviceRuntimeStatusDelegatesToConfigService", controller_test)

    def test_rma_runbook_covers_operator_factory_cleaning_and_refusal_drills(self):
        text = RMA_RUNBOOK.read_text(encoding="utf-8", errors="replace")

        for token in (
            "Operator Access",
            "sys:role:superAdmin",
            "appv2:device:rma",
            "Local Audit Trail",
            "device_rma_events",
            "operator account id",
            "factory cleaning attestation flag and external evidence reference",
            "external RMA ticket reference",
            "factoryCleaned = true",
            "evidenceRef",
            "ticketRef",
            "does not store activation codes or free-form RMA notes",
            "ticket correlation",
            "POST /api/v1/devices/{deviceId}/rma/events",
            "exported `device_rma_events` rows",
            "deployed operator/admin access path",
            "RMA-only operators receive `appv2:device:rma`",
            "local Shiro permission contract",
            "operator identity is tied to a named person or service account",
            "RMA action is approved in a ticket before execution",
            "request id, operator id, device id, device sn, action, and timestamp are recorded",
            "non-operator accounts cannot call RMA endpoints",
            "Factory Credential Cleaning",
            "attach photo or serial-console evidence to the RMA ticket",
            "POST /api/v1/devices/{deviceId}/dispose",
            "POST /api/v1/devices/{deviceId}/rma/events",
            "POST /config/device-runtime-status",
            "E_DEVICE_DISPOSED",
            "manager-api.url",
            "manager-api.secret",
            "Disposed Device Refusal Drill",
            "local whitelist or token bypass does not admit the disposed device",
            "Restock Drill",
            "fresh activation code is generated or the approved replacement activation code is stored",
            "RMA Evidence Gap Record",
            "Use this structure when production RMA evidence is missing",
            "missing evidence scope: operator permission assignment, gateway policy, approved RMA ticket, exported audit review, factory credential cleaning proof, disposed-device runtime refusal, or xiaozhi-server WebSocket refusal",
            "fallback path: keep RMA actions restricted to super-admin, hold restock/dispose, keep device out of resale, and rely on BusinessServer disposed-state refusal until deployed DeviceServer refusal evidence is attached",
            "risk acceptance: operations owner, technical owner, and release manager approval reference",
            "rollback trigger: non-operator access succeeds, audit export is missing, factory cleaning proof is missing, disposed-device runtime entrypoint accepts traffic, or xiaozhi-server admits a disposed device",
            "follow-up evidence: role assignment screenshot, gateway policy export, approved RMA ticket, redacted `device_rma_events` export, factory cleaning photo or serial-console log, runtime refusal logs, and WebSocket refusal logs",
            "rtk python -m unittest tests.ci.test_manager_api_device_rma tests.ci.test_xiaozhi_server_disposed_auth -v",
            "repository-level static CI test",
            "Run it from the repository root",
            "do not run the `tests.ci` module path from inside the xiaozhi-server subdirectory",
            "rtk mvn",
            "does not prove production operator permission assignment",
            "ticket correlation",
            "factory credential cleaning",
        ):
            self.assertIn(token, text)

    def test_rma_status_doc_documents_fresh_or_replacement_activation(self):
        text = RMA_STATUS_DOC.read_text(encoding="utf-8", errors="replace")

        self.assertIn("approved replacement code", text)
        self.assertIn("generating a fresh code when omitted", text)
        self.assertIn("activation-code updates select the latest row for the device by `updated_at` desc and activation `id` desc", text)
        self.assertIn("appv2:device:rma", text)
        self.assertIn("device_rma_events", text)
        self.assertIn("factoryCleaned = true", text)
        self.assertIn("evidenceRef", text)
        self.assertIn("ticketRef", text)
        self.assertIn("latest known binding is selected deterministically by `updated_at` and `id`", text)
        self.assertIn("voiceprint cache refresh clears any stale local cache", text)
        self.assertIn("E_DEVICE_DISPOSED` or `DEVICE_NOT_EXIST", text)
        self.assertIn("voice-task submission treats BusinessServer `Result.code != 0` as a failed submission", text)
        self.assertIn("`motion_event`, `device_info`, and `self_check` forwarders treat BusinessServer `Result.code != 0` as rejected forwarding", text)
        self.assertIn("E_RUNTIME_STATUS_UNAVAILABLE", text)
        self.assertIn("configured-but-unreachable Manager API checks fail closed", text)
        self.assertIn("local audit export endpoint", text)
        self.assertIn("production role assignment, gateway policy, exported audit evidence review", text)


if __name__ == "__main__":
    unittest.main()
