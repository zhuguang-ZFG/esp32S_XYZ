import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CHECKLIST = ROOT / "docs" / "M6.3-release-compliance-checklist.md"
STATUS = ROOT / "docs" / "M6.3-compliance-readiness-status.md"
AUDIT = ROOT / "docs" / "M6-closeout-audit.md"
RELEASE_EVIDENCE_RUNBOOK = ROOT / "ops" / "runbooks" / "m6-release-evidence-package.md"
LOCAL_EVIDENCE_MANIFEST = ROOT / "docs" / "M6-local-evidence-manifest.md"
M6_STATUS_DOCS = (
    ROOT / "docs" / "M6.1-privacy-permissions-status.md",
    ROOT / "docs" / "M6.2-privacy-deletion-status.md",
    ROOT / "docs" / "M6.3-compliance-readiness-status.md",
    ROOT / "docs" / "M6.4-primary-session-status.md",
    ROOT / "docs" / "M6.5-device-transfer-status.md",
    ROOT / "docs" / "M6.6-consumables-status.md",
    ROOT / "docs" / "M6.7-device-rma-status.md",
    ROOT / "docs" / "M6.8-monitoring-alerts-status.md",
)


class M6ComplianceChecklistTests(unittest.TestCase):
    def test_release_compliance_checklist_exists_and_covers_m6_3_keywords(self):
        text = CHECKLIST.read_text(encoding="utf-8", errors="replace")

        for token in (
            "ICP 备案",
            "等保 2.0 二级预备",
            "PIPL",
            "隐私",
            "上线前置",
            "必要证据",
            "上线闸门",
        ):
            self.assertIn(token, text)

    def test_release_compliance_checklist_has_required_domains(self):
        text = CHECKLIST.read_text(encoding="utf-8", errors="replace")

        for token in (
            "DeviceServer / BusinessServer 对外域名完成 ICP 备案",
            "小程序后台服务域名与已备案域名一致",
            "系统边界：小程序、BusinessServer、DeviceServer、数据库、对象存储、运维入口",
            "日志留存策略覆盖登录、设备绑定、任务提交、删除操作、OTA",
            "声纹作为敏感个人信息单独同意",
            "14 岁以下儿童数据处理有监护人同意路径",
            "生成式 AI provider 位于合规服务目录或有供应商合规证明",
            "SRRC / CCC / RoHS / 玩具类 GB 6675 路径确认",
        ):
            self.assertIn(token, text)

    def test_release_compliance_checklist_has_cross_function_signoff(self):
        text = CHECKLIST.read_text(encoding="utf-8", errors="replace")

        for token in (
            "产品负责人确认",
            "法务负责人确认",
            "技术负责人确认",
            "运维负责人确认",
        ):
            self.assertIn(token, text)

    def test_status_doc_does_not_claim_external_approval_done(self):
        status = STATUS.read_text(encoding="utf-8", errors="replace")

        self.assertIn("不声称外部备案、等保测评或微信审核已完成", status)
        self.assertIn("没有也不能替代实际办理", status)
        self.assertIn("docs/M6.3-release-compliance-checklist.md", status)

    def test_m6_status_docs_have_evidence_boundary(self):
        for path in M6_STATUS_DOCS:
            with self.subTest(path=path.name):
                text = path.read_text(encoding="utf-8", errors="replace")
                self.assertIn("Evidence Boundary", text)
                self.assertIn("local/design-time evidence only", text)
                self.assertIn("not a completion declaration", text)
                self.assertIn("does not replace", text)

    def test_closeout_audit_covers_all_m6_items_without_claiming_completion(self):
        audit = AUDIT.read_text(encoding="utf-8", errors="replace")

        for token in (
            "M6.1",
            "M6.2",
            "M6.3",
            "M6.4",
            "M6.5",
            "M6.6",
            "M6.7",
            "M6.8",
            "It is not a completion declaration",
            "Residual Launch Gaps",
            "real permission prompts",
            "external",
            "hardware",
            "deployment",
            "product-operation",
            "not ready to be marked complete",
            "payload-minimized local pending notification outbox",
            "deleted-account tombstone refusal",
            "old-token mutation refusal for deleted account tombstones",
            "recorded `run_path` payload, expected mileage calculation, and accepted tolerance requirements",
            "WebSocket token revalidation and deleted-account tombstone refusal",
            "server-secret protected scrape contract",
            "Manager API scrape secret files remain deployment secrets",
            "manager-mobile/manifest.config.ts",
            "manager-mobile/vite.config.ts",
            "generated WeChat build permission artifact",
            "App `onShow` badge restore",
            "best-effort native tabBar badge update and App `onShow` restore",
            "safe provider payload builder",
            "provider sent/failed markers",
            "ops/runbooks/m6-privacy-permissions.md",
            "ops/runbooks/m6-retention-cleanup.md",
            "ops/runbooks/m6-product-notifications.md",
            "ops/runbooks/m6-consumables-hardware.md",
            "ops/runbooks/m6-device-rma.md",
            "appv2:device:rma",
            "local structured RMA audit rows",
            "local factory-cleaning attestation gate",
            "Shiro pass-through for internal runtime endpoints before controller Bearer-token auth",
            "tests/ci/test_manager_api_resource_domain.py",
            "Production role assignment",
            "ops/runbooks/m6-monitoring-alerts.md",
            "ops/runbooks/m6-release-evidence-package.md",
            "rtk corepack pnpm type-check",
            "rtk corepack pnpm run build:mp-weixin",
            "rtk node -e \"...dist/build/mp-weixin/app.json...\"",
            "scope.record,scope.userLocation",
            "rtk python -m unittest discover -s tests -p \"test_*.py\" -v",
            "243 tests OK",
            "119 tests OK",
            "tests.ci.test_ci_workflow_contract",
            "tests.ci.test_runbook_command_contract",
            "13 tests OK",
            "10 tests OK",
            "5 tests OK",
            "222 tests OK",
            "211 tests OK",
            "rtk python tools/validate_schemas.py",
            "validated=62 passed=62 failed=0",
            "rtk python tools/check_gpio.py",
            "rtk python tools/test_check_gpio.py -v",
            "8 tests OK",
            "rtk python -m unittest tools.tests.test_check_gpio -v",
            "7 tests OK",
            "FirmwareReleaseServiceTest",
            "ProductNotificationOutboxServiceImplTest",
            "MonitoringMetricsControllerTest",
            "91 tests OK",
            "9 tests OK",
            "3 tests OK",
            "24 tests OK",
            "18 tests OK",
            "188 tests OK",
            "DONE Build complete",
            "rtk mvn \"-Dtest=MonitoringMetricsServiceImplTest,MonitoringMetricsControllerTest\" test",
            "rtk mvn \"-Dtest=PrivacyDeletionServiceImplTest,ContentAuditLogServiceTest,SafetyAuditServiceTest\" test",
            "ProductNotificationOutboxServiceImplTest,MonitoringMetricsServiceImplTest,MonitoringMetricsControllerTest",
            "PrivacyDeletionServiceImplTest,ContentAuditLogServiceTest,SafetyAuditServiceTest,DeviceSupplyServiceImplTest",
            "DeviceRmaServiceImplTest,ProductNotificationOutboxServiceImplTest,MonitoringMetricsServiceImplTest",
            "tests.ci.test_manager_mobile_device_info tests.ci.test_monitoring_alerts tests.ci.test_docs_m6_compliance_checklist",
            "tests.ci.test_manager_mobile_privacy_permissions tests.ci.test_manager_api_privacy_deletion",
            "tests.ci.test_manager_api_consumables tests.ci.test_manager_api_device_rma",
            "Runbooks: privacy permissions, retention cleanup, product notifications, consumables hardware, device RMA, monitoring alerts, and release evidence package",
            "Runbook command contracts: `tests/ci/test_runbook_command_contract.py` guards `rtk` prefixes",
            "repository-root `tests.ci` commands",
            "repository-relative `ops/` paths",
            "manager-api source-path commands",
            "requires reviewed M5/M6 local manifests plus concrete production-operation evidence",
            "internal runtime endpoint smoke with Bearer-token refusal/acceptance evidence",
            "Manager API scrape-secret config and 401 refusal evidence",
            "platform notifications, native tabBar screenshots, background reminders or release deferral with owner, due date, fallback path, and risk acceptance",
            "retention post-run logs",
            "RMA operator/audit/refusal drills",
            "monitoring delivery",
            "consumables hardware sensing plus recorded mileage payload/calculation/tolerance evidence or limitation signoff",
            "consumable sensing and mileage accuracy with recorded path payload/calculation/tolerance evidence",
            "delivery/rendering evidence or explicit deferral owner, due date, fallback path, and risk acceptance",
        ):
            self.assertIn(token, audit)

    def test_closeout_audit_binds_verification_commands_to_results(self):
        audit = AUDIT.read_text(encoding="utf-8", errors="replace")

        for command, result in (
            (
                'rtk python -m unittest discover -s tests -p "test_*.py" -v',
                "243 tests OK",
            ),
            (
                "rtk python -m unittest tests.ci.test_runbook_command_contract -v",
                "13 tests OK",
            ),
            (
                "rtk python -m unittest tests.ci.test_docs_m6_compliance_checklist -v",
                "10 tests OK",
            ),
            (
                "rtk python tools/validate_schemas.py",
                "validated=62 passed=62 failed=0",
            ),
            (
                "rtk python tools/test_check_gpio.py -v",
                "8 tests OK",
            ),
            (
                "rtk python -m unittest tools.tests.test_check_gpio -v",
                "7 tests OK",
            ),
        ):
            with self.subTest(command=command):
                self.assertIn(f"- `{command}` -> `{result}`", audit)

    def test_release_evidence_package_runbook_covers_external_gates_without_claiming_done(self):
        text = RELEASE_EVIDENCE_RUNBOOK.read_text(encoding="utf-8", errors="replace")

        for token in (
            "M6-release-evidence",
            "docs/M5-local-evidence-manifest.md",
            "docs/M6-local-evidence-manifest.md",
            "00-local-manifests",
            "01-icp-domain",
            "02-mlps-readiness",
            "03-pipl-privacy",
            "04-ai-content-provider",
            "05-hardware-ota-certification",
            "06-operations-runbooks",
            "07-signoff",
            "ICP filing",
            "MLPS assessment",
            "WeChat review",
            "legal signoff",
            "hardware certification",
            "generated WeChat mini-program `dist/build/mp-weixin/app.json` permission artifact",
            "scope.record",
            "scope.userLocation",
            "requiredPrivateInfos: getLocation",
            "WeChat console privacy declaration screenshot",
            "real permission prompt screenshots",
            "WeChat privacy review approval evidence",
            "M5 local evidence manifest plus real evidence closing its open hardware, provisioning, OTA, and production-key gates",
            "M6 local evidence manifest plus reviewed evidence for every remaining M6 open gate",
            "current M6 closeout audit, including the latest local verification outputs",
            "paper sensing, ink/pressure sensing, and real-device mileage accuracy evidence with recorded `run_path` payload, expected mileage calculation, and accepted tolerance, or explicit manual/estimate limitation signoff",
            "platform notification template approval, opt-in, send-attempt log, delivery screenshot, and deep-link screenshot for pending device transfer",
            "platform notification template approval, opt-in, send-attempt log, delivery screenshot, and deep-link screenshot for pending primary voice approval",
            "native tabBar badge before/after screenshots on the target WeChat client versions",
            "background reminder delivery evidence or explicit release deferral with owner, due date, fallback path, and risk acceptance",
            "record the owner, due date, fallback path, risk acceptance, rollback trigger, and follow-up evidence",
            "production retention scheduler configuration and post-run deletion logs",
            "production RMA operator permission assignment, gateway policy evidence, exported RMA audit review, factory credential cleaning proof, and deployed DeviceServer refusal drill",
            "deployed internal runtime endpoint smoke",
            "/internal/v1/motion_event",
            "/internal/v1/device_info",
            "/internal/v1/self_check",
            "/internal/v1/voice_task",
            "/internal/v1/voiceprints/cache",
            "/internal/v1/firmware/upgrade-plan",
            "/internal/v1/firmware/install-result",
            "missing or wrong Bearer token refusal",
            "valid internal Bearer token acceptance at the controller boundary",
            "Prometheus scrape health",
            "redacted `authorization.credentials_file` config",
            "redacted `MANAGER_API_SECRET_FILE` or secret mount evidence",
            "401 refusal evidence for a missing or wrong Manager API scrape Bearer token",
            "Grafana dashboard screenshot",
            "DingTalk/WeCom webhook delivery evidence",
            "rtk python -m unittest tests.ci.test_docs_m6_compliance_checklist -v",
            "does not verify the contents of external screenshots",
            "Do not mark M6 complete or production-ready",
            "does not prove external approval",
            "does not prove external approval, WeChat console privacy declarations, real permission prompt behavior",
            "do not replace real hardware, deployment, compliance, or production-operation evidence",
        ):
            self.assertIn(token, text)

    def test_release_evidence_package_operation_runbooks_exist_with_guardrails(self):
        release_text = RELEASE_EVIDENCE_RUNBOOK.read_text(encoding="utf-8", errors="replace")

        for relative in (
            "ops/runbooks/m6-privacy-permissions.md",
            "ops/runbooks/m6-retention-cleanup.md",
            "ops/runbooks/m6-product-notifications.md",
            "ops/runbooks/m6-consumables-hardware.md",
            "ops/runbooks/m6-device-rma.md",
            "ops/runbooks/m6-monitoring-alerts.md",
        ):
            with self.subTest(runbook=relative):
                self.assertIn(relative, release_text)

                path = ROOT / relative
                self.assertTrue(path.exists(), relative)
                text = path.read_text(encoding="utf-8", errors="replace")

                self.assertIn("## Current Local Limitation", text)
                self.assertTrue(
                    "## Local Checks" in text
                    or "## Local Verification" in text
                    or "## Pre-Deployment Checks" in text,
                    relative,
                )

    def test_local_evidence_manifest_maps_m6_items_to_evidence_and_open_gates(self):
        text = LOCAL_EVIDENCE_MANIFEST.read_text(encoding="utf-8", errors="replace")

        for token in (
            "M6 Local Evidence Manifest",
            "not a completion declaration",
            "M6.1 Privacy permissions",
            "M6.2 Data deletion",
            "deleted-account tombstone old-token refusal",
            "DeviceSupplyServiceImpl",
            "MemberServiceImpl",
            "VoiceprintEnrollmentServiceImpl",
            "DeviceRmaServiceImpl",
            "M6.3 Compliance readiness",
            "release package requirements for internal runtime endpoint smoke and Manager API scrape-secret evidence",
            "reviewed production-operation evidence",
            "M6.4 Primary session",
            "background reminders or release deferral with owner, due date, fallback path, and risk acceptance",
            "WebSocket token/account revalidation",
            "M6.5 Device transfer",
            "M6.6 Consumables",
            "recorded `run_path` payload, mileage calculation, and accepted tolerance",
            "M6.7 Device RMA",
            "M6.8 Monitoring alerts",
            "server-secret protected scrape",
            "redacted secret mount evidence",
            "401 refusal for missing or wrong scrape Bearer token",
            "ShiroConfig",
            "ops/monitoring/secrets/.gitignore",
            "manager-mobile/manifest.config.ts",
            "manager-mobile/vite.config.ts",
            "manager-mobile/src/manifest.json",
            "generated `dist/build/mp-weixin/app.json`",
            "generated permission artifact check",
            "product notification outbox queue visibility",
            "payload-minimized `ProductNotificationOutboxService`",
            "safe provider payload builder",
            "provider sent/failed markers",
            "ProductNotificationOutboxServiceImplTest",
            "native tabBar badge helper and App `onShow` badge restore",
            "ops/runbooks/m6-privacy-permissions.md",
            "ops/runbooks/m6-retention-cleanup.md",
            "ops/runbooks/m6-release-evidence-package.md",
            "ops/runbooks/m6-product-notifications.md",
            "ops/runbooks/m6-consumables-hardware.md",
            "ops/runbooks/m6-device-rma.md",
            "super-admin/RMA-only permission gate",
            "device_rma_events` local audit rows",
            "factoryCleaned`/`evidenceRef` restock-dispose gate",
            "ShiroConfig` internal endpoint pass-through to controller Bearer-token auth",
            "tests/ci/test_manager_api_resource_domain.py",
            "Production operator permission assignment",
            "ops/runbooks/m6-monitoring-alerts.md",
            "Runbook Gap Records",
            "These records do not replace external approval, hardware evidence, deployed monitoring, or production-operation evidence",
            "Privacy Permissions Evidence Gap Record",
            "Retention Evidence Gap Record",
            "External evidence gap rule: owner, due date, fallback path, risk acceptance, rollback trigger, and follow-up evidence",
            "Release Deferral Record",
            "Consumables Evidence Gap Record",
            "RMA Evidence Gap Record",
            "Monitoring Evidence Gap Record",
            "Open Gate",
            "Do not mark M6 complete from this manifest alone",
        ):
            self.assertIn(token, text)


if __name__ == "__main__":
    unittest.main()
