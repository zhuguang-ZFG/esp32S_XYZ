import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
API = ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-api"
CONTROLLER = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "controller" / "AppV2Controller.java"
SERVICE = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "impl" / "PrivacyDeletionServiceImpl.java"
APPV2_SERVICE = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "impl" / "AppV2ServiceImpl.java"
DEVICE_SUPPLY_SERVICE = APPV2_SERVICE.with_name("DeviceSupplyServiceImpl.java")
MEMBER_SERVICE = APPV2_SERVICE.with_name("MemberServiceImpl.java")
VOICEPRINT_ENROLLMENT_SERVICE = APPV2_SERVICE.with_name("VoiceprintEnrollmentServiceImpl.java")
DEVICE_RMA_SERVICE = APPV2_SERVICE.with_name("DeviceRmaServiceImpl.java")
SERVICE_INTERFACE = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "PrivacyDeletionService.java"
RETENTION_TASK = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "PrivacyRetentionCleanupTask.java"
CONTENT_RETENTION_TASK = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "contentaudit" / "ContentAuditRetentionTask.java"
SAFETY_RETENTION_TASK = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "safety" / "SafetyAuditRetentionTask.java"
ADMIN_APPLICATION = API / "src" / "main" / "java" / "xiaozhi" / "AdminApplication.java"
ACCOUNT_ENTITY = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "entity" / "V2AccountEntity.java"
VOICEPRINT_ENTITY = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "entity" / "V2VoiceprintEntity.java"
CHANGELOG = API / "src" / "main" / "resources" / "db" / "changelog" / "202605151900.sql"
MASTER = API / "src" / "main" / "resources" / "db" / "changelog" / "db.changelog-master.yaml"
SERVICE_TEST = API / "src" / "test" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "PrivacyDeletionServiceImplTest.java"
APPV2_SERVICE_TEST = API / "src" / "test" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "AppV2ServiceImplTest.java"
DEVICE_SUPPLY_SERVICE_TEST = APPV2_SERVICE_TEST.with_name("DeviceSupplyServiceImplTest.java")
MEMBER_SERVICE_TEST = APPV2_SERVICE_TEST.with_name("MemberServiceImplTest.java")
VOICEPRINT_ENROLLMENT_SERVICE_TEST = APPV2_SERVICE_TEST.with_name("VoiceprintEnrollmentServiceImplTest.java")
DEVICE_RMA_SERVICE_TEST = APPV2_SERVICE_TEST.with_name("DeviceRmaServiceImplTest.java")
MOBILE_API = ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-mobile" / "src" / "api" / "v2" / "index.ts"
MOBILE_TYPES = ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-mobile" / "src" / "api" / "v2" / "types.ts"
MOBILE_SETTINGS = ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-mobile" / "src" / "pages" / "settings" / "index.vue"
RETENTION_RUNBOOK = ROOT / "ops" / "runbooks" / "m6-retention-cleanup.md"


class ManagerApiPrivacyDeletionTests(unittest.TestCase):
    def test_privacy_deletion_endpoints_exist(self):
        text = CONTROLLER.read_text(encoding="utf-8", errors="replace")

        self.assertIn('"/voiceprints/{voiceprintId}/delete"', text)
        self.assertIn('"/account/delete"', text)
        self.assertIn("PrivacyDeletionService", text)
        self.assertIn("deleteVoiceprint(voiceprintId)", text)
        self.assertIn("deleteAccount()", text)

    def test_privacy_deletion_service_anonymizes_sensitive_fields(self):
        text = SERVICE.read_text(encoding="utf-8", errors="replace")

        for token in (
            "STATUS_DELETED",
            "AUDIT_RETENTION_DAYS = 180",
            "setSpeakerRef(deletedSpeakerRef",
            "setEmbeddingHash(deletedEmbeddingHash())",
            '"0".repeat(64)',
            "setPrimarySessionId(null)",
            "setPrimarySessionClaimedAt(null)",
            "setOpenid(null)",
            "setDisplayName(\"deleted-account-\"",
            "BINDING_STATUS_UNBOUND",
            ".set(\"unbound_at\", now)",
            "setAuditRetainUntil",
            "SysUserTokenService",
            "sysUserTokenService.logout(user.getId())",
        ):
            self.assertIn(token, text)

    def test_deleted_account_tombstone_is_not_reactivated_by_login(self):
        service = APPV2_SERVICE.read_text(encoding="utf-8", errors="replace")
        service_test = APPV2_SERVICE_TEST.read_text(encoding="utf-8", errors="replace")
        docs = (ROOT / "docs" / "M6.2-privacy-deletion-status.md").read_text(encoding="utf-8", errors="replace")

        self.assertIn('ACCOUNT_STATUS_DELETED = "deleted"', service)
        self.assertIn("ACCOUNT_STATUS_DELETED.equalsIgnoreCase(account.getStatus())", service)
        self.assertIn("throw new RenException(ErrorCode.ACCOUNT_DISABLE)", service)
        self.assertIn("v2AccountDao.selectById(user.getId())", service)
        self.assertIn("loginRejectsDeletedAccountTombstone", service_test)
        self.assertIn("bindDeviceRejectsDeletedAccountTombstoneBeforeActivationLookup", service_test)
        self.assertIn("never()).getByUsername", service_test)
        self.assertIn("never()).save", service_test)
        self.assertIn("never()).createToken", service_test)
        self.assertIn("never()).selectOne", service_test)
        self.assertIn("never()).updateById", service_test)
        self.assertIn("deleted account tombstone is not reactivated by login", docs)

    def test_deleted_account_tombstone_is_rejected_by_secondary_mutation_services(self):
        docs = (ROOT / "docs" / "M6.2-privacy-deletion-status.md").read_text(encoding="utf-8", errors="replace")
        service_and_test_pairs = (
            (DEVICE_SUPPLY_SERVICE, DEVICE_SUPPLY_SERVICE_TEST, "updateSuppliesRejectsDeletedAccountTombstoneBeforeBindingLookup"),
            (MEMBER_SERVICE, MEMBER_SERVICE_TEST, "createRejectsDeletedAccountTombstoneBeforeBindingLookup"),
            (VOICEPRINT_ENROLLMENT_SERVICE, VOICEPRINT_ENROLLMENT_SERVICE_TEST, "enrollRejectsDeletedAccountTombstoneBeforeBindingLookup"),
            (DEVICE_RMA_SERVICE, DEVICE_RMA_SERVICE_TEST, "startRepairRejectsDeletedOperatorAccountBeforeDeviceLookup"),
        )

        for service_path, test_path, test_name in service_and_test_pairs:
            with self.subTest(service=service_path.name):
                service = service_path.read_text(encoding="utf-8", errors="replace")
                service_test = test_path.read_text(encoding="utf-8", errors="replace")

                self.assertIn("V2AccountDao", service)
                self.assertIn('ACCOUNT_STATUS_DELETED = "deleted"', service)
                self.assertIn("v2AccountDao.selectById(user.getId())", service)
                self.assertIn("throw new RenException(ErrorCode.ACCOUNT_DISABLE)", service)
                self.assertIn(test_name, service_test)
                self.assertIn("never()).selectOne", service_test)

        voiceprint_service = VOICEPRINT_ENROLLMENT_SERVICE.read_text(encoding="utf-8", errors="replace")
        voiceprint_test = VOICEPRINT_ENROLLMENT_SERVICE_TEST.read_text(encoding="utf-8", errors="replace")
        self.assertIn("v2AccountDao.selectById(binding.getAccountId())", voiceprint_service)
        self.assertIn("activeCacheForDeviceReturnsEmptyWhenBindingAccountIsDeleted", voiceprint_test)
        self.assertIn("Device supply, member management, voiceprint enrollment, voiceprint cache, and RMA mutation paths reject deleted account tombstones", docs)

    def test_privacy_deletion_audit_retention_fields_are_migrated(self):
        account = ACCOUNT_ENTITY.read_text(encoding="utf-8", errors="replace")
        voiceprint = VOICEPRINT_ENTITY.read_text(encoding="utf-8", errors="replace")
        migration = CHANGELOG.read_text(encoding="utf-8", errors="replace")
        master = MASTER.read_text(encoding="utf-8", errors="replace")

        self.assertIn("private Date deletedAt", account)
        self.assertIn("private Date auditRetainUntil", account)
        self.assertIn("private Date deletedAt", voiceprint)
        self.assertIn("private Date auditRetainUntil", voiceprint)
        self.assertIn("ALTER TABLE `accounts`", migration)
        self.assertIn("ALTER TABLE `voiceprints`", migration)
        self.assertIn("`audit_retain_until`", migration)
        self.assertIn("202605151900.sql", master)

    def test_privacy_deletion_has_account_and_voiceprint_drills(self):
        text = SERVICE_TEST.read_text(encoding="utf-8", errors="replace")

        self.assertIn("deleteVoiceprintAnonymizesMatchingMaterialAndKeepsAuditRetention", text)
        self.assertIn("deleteAccountSoftDeletesAccountBindingsMembersAndVoiceprints", text)
        self.assertIn("assertEquals(\"deleted\"", text)
        self.assertIn("assertNotNull(deleted.getAuditRetainUntil())", text)
        self.assertIn("assertNotNull(deletedAccount.getAuditRetainUntil())", text)
        self.assertIn("assertNull(deletedAccount.getPrimarySessionClaimedAt())", text)
        self.assertIn("verify(sysUserTokenService).logout(31L)", text)

    def test_retention_expiry_cleanup_job_exists(self):
        service_interface = SERVICE_INTERFACE.read_text(encoding="utf-8", errors="replace")
        service = SERVICE.read_text(encoding="utf-8", errors="replace")
        task = RETENTION_TASK.read_text(encoding="utf-8", errors="replace")
        content_task = CONTENT_RETENTION_TASK.read_text(encoding="utf-8", errors="replace")
        safety_task = SAFETY_RETENTION_TASK.read_text(encoding="utf-8", errors="replace")
        app = ADMIN_APPLICATION.read_text(encoding="utf-8", errors="replace")
        service_test = SERVICE_TEST.read_text(encoding="utf-8", errors="replace")

        self.assertIn("int purgeExpiredRetention(Date now)", service_interface)
        self.assertIn("purgeExpiredRetention(Date now)", service)
        self.assertIn("v2VoiceprintDao.delete", service)
        self.assertIn("V2VoiceprintEntity::getAuditRetainUntil", service)
        self.assertIn('.eq(V2VoiceprintEntity::getStatus, STATUS_DELETED)', service)
        self.assertIn('.le(V2VoiceprintEntity::getAuditRetainUntil, now)', service)
        self.assertIn('.set("display_name", null)', service)
        self.assertIn('.set("audit_retain_until", null)', service)
        self.assertIn("@Scheduled(cron = \"${appv2.privacy.retention-cleanup-cron:0 50 3 * * *}\")", task)
        self.assertIn("privacyDeletionService.purgeExpiredRetention(new Date())", task)
        self.assertIn("log.info(\"purged expired privacy retention rows count={}\", affectedRows)", task)
        self.assertNotIn("if (affectedRows > 0)", task)
        self.assertIn("log.info(\"purged expired content_audit rows count={}\", deleted)", content_task)
        self.assertNotIn("if (deleted > 0)", content_task)
        self.assertIn("log.info(\"purged expired safety_audit rows count={}\", deleted)", safety_task)
        self.assertNotIn("if (deleted > 0)", safety_task)
        self.assertIn("@EnableScheduling", app)
        self.assertIn("purgeExpiredRetentionDeletesExpiredVoiceprintsAndClearsAccountTombstones", service_test)

    def test_retention_cleanup_runbook_covers_schedules_evidence_and_limits(self):
        text = RETENTION_RUNBOOK.read_text(encoding="utf-8", errors="replace")

        for token in (
            "PrivacyRetentionCleanupTask",
            "ContentAuditRetentionTask",
            "SafetyAuditRetentionTask",
            "appv2.privacy.retention-cleanup-cron",
            "appv2.content.audit.retention-cron",
            "appv2.safety.audit.retention-cron",
            "rtk python -m unittest",
            "rtk mvn",
            "Get-ChildItem -Path 'src/main/java' -Recurse -Filter '*.java'",
            "Select-String -Pattern '@EnableScheduling|retention-cleanup-cron|audit.retention-cron' -List",
            "Dry-Run Evidence Queries",
            "expired_deleted_voiceprints",
            "expired_deleted_account_tombstones",
            "expired_content_audit_rows",
            "expired_safety_audit_rows",
            "FROM voiceprints",
            "FROM accounts",
            "FROM content_audit",
            "FROM safety_audit",
            "audit_retain_until <= CURRENT_TIMESTAMP",
            "INTERVAL 180 DAY",
            "expected upper bound for the next cleanup pass",
            "Post-Run Checks",
            "including zero-row runs",
            "privacy retention cleanup task failed",
            "content_audit retention task failed",
            "safety_audit retention task failed",
            "Retention Evidence Gap Record",
            "Use this structure when production scheduler or post-run cleanup evidence is missing",
            "missing evidence scope: loaded cron configuration, scheduler enabled, pre-run counts, post-run logs, failure-log absence, post-run counts, backup coverage, or PITR readiness",
            "fallback path: keep cleanup disabled, keep retention rows intact, run read-only dry-run queries, and hold release if expired sensitive rows cannot be explained",
            "risk acceptance: privacy owner, technical owner, and release manager approval reference",
            "rollback trigger: unexpected affected-row count, missing backup/PITR proof, cleanup failure log, or unexplained post-run count delta",
            "follow-up evidence: rendered cron configuration, scheduler startup log, pre-run query output, post-run cleanup log, post-run query output, backup/PITR proof, and incident record if rollback is invoked",
            "Rollback And Incident Handling",
            "confirm backups cover",
            "point-in-time restore is available",
            "does not prove the production scheduler has run",
            "Production evidence must be attached to the release or operations ticket",
        ):
            self.assertIn(token, text)
        self.assertNotIn("src/main/java/xiaozhi/**/*.java", text)

    def test_manager_mobile_exposes_account_deletion_confirmation_ui(self):
        api = MOBILE_API.read_text(encoding="utf-8", errors="replace")
        types = MOBILE_TYPES.read_text(encoding="utf-8", errors="replace")
        settings = MOBILE_SETTINGS.read_text(encoding="utf-8", errors="replace")

        self.assertIn("V2DeletionResponse", types)
        self.assertIn("v2DeleteAccount", api)
        self.assertIn("'/api/v1/account/delete'", api)
        self.assertIn("handleAccountDeletion", settings)
        self.assertIn("submitAccountDeletion", settings)
        self.assertIn("v2DeleteAccount()", settings)
        self.assertIn("This action requires a second confirmation", settings)
        self.assertIn("Confirm deletion", settings)
        self.assertIn("clearAllCacheAfterUrlChange()", settings)
        self.assertIn("uni.reLaunch({ url: '/pages/v2/login/index' })", settings)


if __name__ == "__main__":
    unittest.main()
