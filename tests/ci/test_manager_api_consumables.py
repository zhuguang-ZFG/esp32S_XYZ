import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
API = ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-api"

CONTROLLER = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "controller" / "AppV2Controller.java"
SERVICE = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "impl" / "DeviceSupplyServiceImpl.java"
APP_SERVICE = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "impl" / "AppV2ServiceImpl.java"
SAFETY_CODES = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "safety" / "SafetyErrorCode.java"
SQL = API / "src" / "main" / "resources" / "db" / "changelog" / "202605160030.sql"
TASK_DISPATCH_SQL = API / "src" / "main" / "resources" / "db" / "changelog" / "202605160150.sql"
MASTER = API / "src" / "main" / "resources" / "db" / "changelog" / "db.changelog-master.yaml"
CONSUMABLES_RUNBOOK = ROOT / "ops" / "runbooks" / "m6-consumables-hardware.md"
CONSUMABLES_STATUS = ROOT / "docs" / "M6.6-consumables-status.md"


class ManagerApiConsumablesTest(unittest.TestCase):
    def test_device_supplies_table_is_registered(self):
        sql = SQL.read_text(encoding="utf-8", errors="replace")
        master = MASTER.read_text(encoding="utf-8", errors="replace")

        self.assertIn("CREATE TABLE IF NOT EXISTS `device_supplies`", sql)
        self.assertIn("paper_slot_state", sql)
        self.assertIn("pen_installed_at", sql)
        self.assertIn("pen_ink_percent_est", sql)
        self.assertIn("pen_mileage_mm", sql)
        self.assertIn("202605160030.sql", master)

    def test_supply_update_endpoint_exists(self):
        text = CONTROLLER.read_text(encoding="utf-8", errors="replace")
        service = SERVICE.read_text(encoding="utf-8", errors="replace")
        ensure_binding = service[service.index("private void ensureActiveBinding"):]

        self.assertIn('"/devices/{deviceId}/supplies"', text)
        self.assertIn("DeviceSupplyService", text)
        self.assertIn("updateDeviceSupplies", text)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getUpdatedAt)", ensure_binding)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getId)", ensure_binding)

    def test_paper_gate_contract_exists(self):
        service = SERVICE.read_text(encoding="utf-8", errors="replace")
        app_service = APP_SERVICE.read_text(encoding="utf-8", errors="replace")
        safety_codes = SAFETY_CODES.read_text(encoding="utf-8", errors="replace")

        self.assertIn("E_NO_PAPER", safety_codes)
        self.assertIn("paper slot is marked empty", service)
        self.assertIn('"write_text"', service)
        self.assertIn('"draw"', service)
        self.assertIn('"draw_generated"', service)
        self.assertIn("normalizePaperSlotState", service)
        self.assertIn("normalizeInkPercent", service)
        self.assertIn("value < 0 || value > 100", service)
        self.assertIn("throw new RenException(ErrorCode.PARAMS_GET_ERROR)", service)
        self.assertIn("deviceSupplyService.requirePaperReadyForWrite", app_service)
        self.assertLess(
            app_service.index("deviceSupplyService.requirePaperReadyForWrite"),
            app_service.index("resourceEntitlementService.requireSubmitEntitlements"),
        )

    def test_run_path_completion_accumulates_pen_mileage(self):
        service = SERVICE.read_text(encoding="utf-8", errors="replace")
        app_service = APP_SERVICE.read_text(encoding="utf-8", errors="replace")

        self.assertIn("recordCompletedRunPathMileage", service)
        self.assertIn("calculateRunPathMileageMm", service)
        self.assertIn("setPenMileageMm(current.add(mileageMm))", service)
        self.assertIn("recordRunPathMileageIfNewlyDone", app_service)
        self.assertIn("deviceSupplyService.recordCompletedRunPathMileage", app_service)
        self.assertIn('TASK_STATUS_DONE.equals(previousStatus)', app_service)
        self.assertIn("setDispatchCapability(dispatchRequest.getCapability())", app_service)
        self.assertIn("setDispatchParamsJson(toJson(dispatchRequest.getParams()))", app_service)
        self.assertIn("StringUtils.defaultIfBlank(task.getDispatchCapability(), task.getCapability())", app_service)
        self.assertIn("StringUtils.defaultIfBlank(task.getDispatchParamsJson(), task.getParamsJson())", app_service)

        dispatch_sql = TASK_DISPATCH_SQL.read_text(encoding="utf-8", errors="replace")
        master = MASTER.read_text(encoding="utf-8", errors="replace")
        self.assertIn("dispatch_capability", dispatch_sql)
        self.assertIn("dispatch_params_json", dispatch_sql)
        self.assertIn("202605160150.sql", master)

    def test_consumables_hardware_runbook_covers_real_device_acceptance_and_limits(self):
        text = CONSUMABLES_RUNBOOK.read_text(encoding="utf-8", errors="replace")

        for token in (
            "paper_slot_state",
            "pen_installed_at",
            "pen_ink_percent_est",
            "pen_mileage_mm",
            "E_NO_PAPER",
            "manual/estimated",
            "rtk python -m unittest tests.ci.test_manager_api_consumables tests.ci.test_manager_mobile_device_info -v",
            "rtk mvn",
            "rtk corepack pnpm type-check",
            "Device Acceptance Drill",
            "pen_ink_percent_est = 101",
            "pen_ink_percent_est = -1",
            "rejects it without insert/update",
            "Record the exact `run_path` payload",
            "sum each line segment as `sqrt(dx^2 + dy^2 + dz^2)`",
            "within <= 1 mm tolerance",
            "firmware/controller rounding rule",
            "unknown",
            "does not block write/draw submission",
            "device id and firmware version",
            "manager-mobile screenshots for empty, loaded, unknown, and new-pen states",
            "API request/response logs for invalid `pen_ink_percent_est` values",
            "exact `run_path` payload, expected mileage calculation, and accepted tolerance",
            "before/after `pen_mileage_mm`",
            "Sensor Integration Gate",
            "UI state when sensor data is stale or unavailable",
            "tests showing sensor-backed state can override or inform manual state",
            "Consumables Evidence Gap Record",
            "Use this structure when consumables hardware or mileage evidence is missing",
            "missing evidence scope: paper state screenshots, `E_NO_PAPER` request logs, invalid ink-percent request logs, exact `run_path` payload, expected mileage calculation, before/after `pen_mileage_mm`, paper sensing, ink sensing, pressure sensing, or sensor stale-state UI",
            "fallback path: keep consumables labeled manual/estimated, keep `unknown` paper state allowed, require user-marked `empty` for blocking, and do not claim sensor-backed paper or ink status",
            "risk acceptance: product owner, hardware owner, and release manager approval reference",
            "rollback trigger: physical paper state disagrees with UI, `E_NO_PAPER` gate fails, invalid ink percent persists, mileage delta exceeds accepted tolerance without a documented rounding rule, or sensor-backed state is claimed without evidence",
            "follow-up evidence: device id, firmware version, mobile screenshots, redacted API logs, exact `run_path` payload, expected mileage calculation, motion `done` event, before/after mileage values, tolerance note, sensor calibration proof, and stale-state UI screenshot",
            "does not prove physical paper sensing",
            "ink sensing",
            "pressure sensing",
            "real-device mileage accuracy",
        ):
            self.assertIn(token, text)

    def test_consumables_status_documents_unknown_paper_state_limit(self):
        text = CONSUMABLES_STATUS.read_text(encoding="utf-8", errors="replace")

        self.assertIn("`unknown` paper state is allowed through", text)
        self.assertIn("only user-marked `empty` blocks writes/draws", text)
        self.assertIn("`pen_ink_percent_est` accepts only integer values from 0 to 100", text)
        self.assertIn("out-of-range values are rejected before insert/update", text)
        self.assertIn("Static CI guards `pen_ink_percent_est` range validation", text)
        self.assertIn("manual supply updates select active bindings by `updated_at` desc and binding `id` desc", text)
        self.assertIn("exact `run_path` payload", text)
        self.assertIn("expected mileage calculation using `sqrt(dx^2 + dy^2 + dz^2)`", text)
        self.assertIn("<= 1 mm tolerance", text)
        self.assertIn("firmware/controller rounding evidence", text)


if __name__ == "__main__":
    unittest.main()
