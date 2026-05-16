import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CHANGELOG_MASTER = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-api"
    / "src"
    / "main"
    / "resources"
    / "db"
    / "changelog"
    / "db.changelog-master.yaml"
)
RESOURCE_SQL = CHANGELOG_MASTER.with_name("202605151610.sql")
VOICEPRINT_SQL = CHANGELOG_MASTER.with_name("202605151650.sql")
FIRMWARE_RELEASE_SQL = CHANGELOG_MASTER.with_name("202605151720.sql")
SELF_CHECK_HISTORY_SQL = CHANGELOG_MASTER.with_name("202605160145.sql")
APP_V2_CONTROLLER = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-api"
    / "src"
    / "main"
    / "java"
    / "xiaozhi"
    / "modules"
    / "appv2"
    / "controller"
    / "AppV2Controller.java"
)
FIRMWARE_RELEASE_CONTROLLER = APP_V2_CONTROLLER.with_name("FirmwareReleaseController.java")
VOICEPRINT_SERVICE = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-api"
    / "src"
    / "main"
    / "java"
    / "xiaozhi"
    / "modules"
    / "appv2"
    / "service"
    / "impl"
    / "VoiceprintEnrollmentServiceImpl.java"
)
MEMBER_SERVICE = VOICEPRINT_SERVICE.with_name("MemberServiceImpl.java")
INTERNAL_CONTROLLER = APP_V2_CONTROLLER.with_name("InternalMotionEventController.java")
DEVICE_VOICEPRINT_CACHE = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "xiaozhi-server"
    / "core"
    / "utils"
    / "voiceprint_cache.py"
)
DEVICE_OTA_HANDLER = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "xiaozhi-server"
    / "core"
    / "api"
    / "ota_handler.py"
)
DEVICE_HTTP_SERVER = DEVICE_OTA_HANDLER.parent.parent / "http_server.py"
STARTER_ASSET_CATALOG = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-api"
    / "src"
    / "main"
    / "java"
    / "xiaozhi"
    / "modules"
    / "appv2"
    / "service"
    / "projection"
    / "StarterAssetCatalog.java"
)
FACTORY_ENTITLEMENT_SERVICE = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-api"
    / "src"
    / "main"
    / "java"
    / "xiaozhi"
    / "modules"
    / "appv2"
    / "service"
    / "resource"
    / "FactoryEntitlementService.java"
)
RESOURCE_ENTITLEMENT_SERVICE = FACTORY_ENTITLEMENT_SERVICE.with_name("ResourceEntitlementService.java")
ENTITLEMENT_VALIDATION_EXCEPTION = FACTORY_ENTITLEMENT_SERVICE.with_name("EntitlementValidationException.java")
FIRMWARE_RELEASE_SERVICE = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-api"
    / "src"
    / "main"
    / "java"
    / "xiaozhi"
    / "modules"
    / "appv2"
    / "service"
    / "firmware"
    / "FirmwareReleaseService.java"
)
APP_V2_SERVICE_IMPL = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-api"
    / "src"
    / "main"
    / "java"
    / "xiaozhi"
    / "modules"
    / "appv2"
    / "service"
    / "impl"
    / "AppV2ServiceImpl.java"
)
SAFETY_ERROR_CODE = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-api"
    / "src"
    / "main"
    / "java"
    / "xiaozhi"
    / "modules"
    / "appv2"
    / "service"
    / "safety"
    / "SafetyErrorCode.java"
)
SHIRO_CONFIG = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-api"
    / "src"
    / "main"
    / "java"
    / "xiaozhi"
    / "modules"
    / "security"
    / "config"
    / "ShiroConfig.java"
)
FIRMWARE_RELEASE_ENTITY = (
    ROOT
    / "server"
    / "xiaozhi-esp32-server"
    / "main"
    / "manager-api"
    / "src"
    / "main"
    / "java"
    / "xiaozhi"
    / "modules"
    / "appv2"
    / "entity"
    / "V2FirmwareReleaseEntity.java"
)
FIRMWARE_RELEASE_DAO = FIRMWARE_RELEASE_ENTITY.parent.parent / "dao" / "V2FirmwareReleaseDao.java"
FIRMWARE_RELEASE_PUBLISH_REQUEST = FIRMWARE_RELEASE_ENTITY.parent.parent / "dto" / "V2FirmwareReleasePublishRequest.java"
FIRMWARE_INSTALL_RESULT_REQUEST = FIRMWARE_RELEASE_ENTITY.parent.parent / "dto" / "V2FirmwareInstallResultRequest.java"
FIRMWARE_RELEASE_RESPONSE = FIRMWARE_RELEASE_ENTITY.parent.parent / "dto" / "V2FirmwareReleaseResponse.java"
SELF_CHECK_EVENT_ENTITY = FIRMWARE_RELEASE_ENTITY.with_name("V2DeviceSelfCheckEventEntity.java")
SELF_CHECK_EVENT_DAO = FIRMWARE_RELEASE_DAO.with_name("V2DeviceSelfCheckEventDao.java")
SELF_CHECK_HISTORY_RESPONSE = FIRMWARE_RELEASE_RESPONSE.with_name("V2SelfCheckHistoryResponse.java")


class ManagerApiResourceDomainTests(unittest.TestCase):
    def test_resource_domain_changelog_is_registered(self):
        text = CHANGELOG_MASTER.read_text(encoding="utf-8", errors="replace")

        self.assertIn("id: 202605151610", text)
        self.assertIn("path: classpath:db/changelog/202605151610.sql", text)

    def test_resource_domain_tables_match_v2_contract(self):
        text = RESOURCE_SQL.read_text(encoding="utf-8", errors="replace")

        for table in (
            "`fonts`",
            "`copybooks`",
            "`assets`",
            "`ai_plans`",
            "`entitlements`",
        ):
            self.assertIn(f"CREATE TABLE IF NOT EXISTS {table}", text)
        for column in (
            "`font_id` VARCHAR(64)",
            "`copybook_id` VARCHAR(64)",
            "`asset_id` VARCHAR(64)",
            "`fallback_only` TINYINT(1)",
            "`plan_id` VARCHAR(64)",
            "`provider` VARCHAR(64)",
            "`resource_type` VARCHAR(32)",
            "`resource_id` VARCHAR(64)",
            "`source` VARCHAR(32)",
            "`expires_at` DATETIME",
        ):
            self.assertIn(column, text)
        self.assertIn("uk_entitlements_account_resource", text)
        self.assertIn("idx_entitlements_resource", text)
        self.assertIn("idx_ai_plans_provider", text)

    def test_factory_free_resources_are_seeded(self):
        text = RESOURCE_SQL.read_text(encoding="utf-8", errors="replace")

        self.assertIn("INSERT INTO `fonts`", text)
        self.assertIn("'kai_basic_v1'", text)
        self.assertIn("INSERT INTO `copybooks`", text)
        self.assertIn("'pinyin_basic_v1'", text)
        self.assertIn("INSERT INTO `assets`", text)
        self.assertIn("INSERT INTO `ai_plans`", text)
        self.assertIn("'local_fake_ai_plan_v1'", text)
        self.assertIn("'local_fake_ai'", text)
        for asset_id in ("starter_star", "starter_house", "starter_tree", "starter_fish", "starter_flower"):
            self.assertIn(f"'{asset_id}'", text)
        self.assertIn("ON DUPLICATE KEY UPDATE", text)

    def test_seeded_starter_assets_match_projection_catalog(self):
        sql = RESOURCE_SQL.read_text(encoding="utf-8", errors="replace")
        catalog = STARTER_ASSET_CATALOG.read_text(encoding="utf-8", errors="replace")

        for asset_id in ("starter_star", "starter_house", "starter_tree", "starter_fish", "starter_flower"):
            self.assertIn(f"'{asset_id}'", sql)
            self.assertIn(f'"{asset_id}"', catalog)

    def test_factory_entitlement_service_injects_seeded_free_resources(self):
        sql = RESOURCE_SQL.read_text(encoding="utf-8", errors="replace")
        service = FACTORY_ENTITLEMENT_SERVICE.read_text(encoding="utf-8", errors="replace")
        resource_service = RESOURCE_ENTITLEMENT_SERVICE.read_text(encoding="utf-8", errors="replace")
        factory_sources = service + resource_service

        self.assertIn("ensureFactoryEntitlements", service)
        self.assertIn('"factory"', service)
        for resource_id in (
            "kai_basic_v1",
            "pinyin_basic_v1",
            "starter_star",
            "starter_house",
            "starter_tree",
            "starter_fish",
            "starter_flower",
            "local_fake_ai_plan_v1",
        ):
            self.assertIn(resource_id, sql)
            self.assertIn(resource_id, factory_sources)

    def test_bind_device_uses_deterministic_active_binding_lookup(self):
        service = APP_V2_SERVICE_IMPL.read_text(encoding="utf-8", errors="replace")
        bind_method = service[service.index("public V2BindDeviceResponse bindDevice"):]
        bind_method = bind_method[:bind_method.index("private void ensureBoundDeviceState")]

        self.assertIn("V2DeviceBindingEntity binding = activeBindingByDevice(deviceId)", bind_method)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getUpdatedAt)", service)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getId)", service)

    def test_submit_entitlement_service_checks_consumed_resources(self):
        service = RESOURCE_ENTITLEMENT_SERVICE.read_text(encoding="utf-8", errors="replace")
        exception = ENTITLEMENT_VALIDATION_EXCEPTION.read_text(encoding="utf-8", errors="replace")

        self.assertIn("requireSubmitEntitlements", service)
        self.assertIn("WriteTextProjectionService.DEFAULT_FONT_ID", service)
        self.assertIn('"font"', service)
        self.assertIn('"asset"', service)
        self.assertIn('"ai_plan"', service)
        self.assertIn("DEFAULT_DRAW_GENERATED_AI_PLAN_ID", service)
        self.assertIn("local_fake_ai_plan_v1", service)
        self.assertIn("requiresAiPlan", service)
        for key in ("starter_id", "starter_asset_id", "preset_id", "use_starter_asset"):
            self.assertIn(key, service)
        self.assertIn("V2EntitlementEntity::getExpiresAt", service)
        self.assertIn(".isNull(V2EntitlementEntity::getExpiresAt)", service)
        self.assertIn(".gt(V2EntitlementEntity::getExpiresAt", service)
        self.assertIn("E_NOT_ENTITLED", exception)

    def test_voiceprint_domain_changelog_is_registered(self):
        text = CHANGELOG_MASTER.read_text(encoding="utf-8", errors="replace")

        self.assertIn("id: 202605151650", text)
        self.assertIn("path: classpath:db/changelog/202605151650.sql", text)

    def test_voiceprint_tables_store_metadata_not_raw_audio(self):
        text = VOICEPRINT_SQL.read_text(encoding="utf-8", errors="replace")

        self.assertIn("CREATE TABLE IF NOT EXISTS `members`", text)
        self.assertIn("CREATE TABLE IF NOT EXISTS `voiceprints`", text)
        for column in (
            "`account_id` BIGINT",
            "`device_id` VARCHAR(64)",
            "`member_type` VARCHAR(32)",
            "`speaker_ref` VARCHAR(128)",
            "`embedding_hash` CHAR(64)",
            "`sample_duration_ms` INT",
            "`enrolled_at` DATETIME",
            "`expires_at` DATETIME",
        ):
            self.assertIn(column, text)
        self.assertIn("raw audio is not stored", text)
        self.assertNotIn("audio_base64", text.lower())
        self.assertNotIn("audio_blob", text.lower())
        self.assertNotIn("longblob", text.lower())

    def test_voiceprint_enrollment_endpoint_and_service_are_minimal(self):
        controller = APP_V2_CONTROLLER.read_text(encoding="utf-8", errors="replace")
        service = VOICEPRINT_SERVICE.read_text(encoding="utf-8", errors="replace")
        ensure_binding = service[service.index("private void ensureActiveBinding"):]
        ensure_binding = ensure_binding[:ensure_binding.index("private V2DeviceBindingEntity activeBindingByDevice")]

        self.assertIn('/voiceprints/enroll', controller)
        self.assertIn("VoiceprintEnrollmentService", controller)
        self.assertIn("MIN_SAMPLE_DURATION_MS = 5_000", service)
        self.assertIn("MAX_SAMPLE_DURATION_MS = 8_000", service)
        self.assertIn("Base64.getDecoder().decode", service)
        self.assertIn("MessageDigest.getInstance(\"SHA-256\")", service)
        self.assertIn("local_fake_voiceprint", service)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getUpdatedAt)", ensure_binding)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getId)", ensure_binding)
        self.assertNotIn("setAudioBase64", service)

    def test_voiceprint_cache_endpoint_and_device_policy_exist(self):
        internal_controller = INTERNAL_CONTROLLER.read_text(encoding="utf-8", errors="replace")
        service = VOICEPRINT_SERVICE.read_text(encoding="utf-8", errors="replace")
        device_cache = DEVICE_VOICEPRINT_CACHE.read_text(encoding="utf-8", errors="replace")

        self.assertIn('/voiceprints/cache', internal_controller)
        self.assertIn("activeCacheForDevice", service)
        self.assertIn("activeBindingByDevice(normalizedDeviceId)", service)
        self.assertIn("V2VoiceprintEntity::getAccountId, binding.getAccountId()", service)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getUpdatedAt)", service)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getId)", service)
        self.assertIn("V2VoiceprintCacheEntry", service)
        self.assertIn("getExpiresAt", service)
        self.assertIn("ActiveVoiceprintCache", device_cache)
        self.assertIn("decide_voiceprint_policy", device_cache)
        self.assertIn("voiceprint_off", device_cache)
        self.assertIn("loose", device_cache)
        self.assertIn("strict", device_cache)
        self.assertIn("child_reenroll_required", device_cache)
        self.assertIn("child_unknown_allowed", device_cache)

    def test_member_management_endpoint_and_service_support_owner_child(self):
        controller = APP_V2_CONTROLLER.read_text(encoding="utf-8", errors="replace")
        service = MEMBER_SERVICE.read_text(encoding="utf-8", errors="replace")
        ensure_binding = service[service.index("private void ensureActiveBinding"):]

        self.assertIn('/members', controller)
        self.assertIn('/devices/{deviceId}/members/list', controller)
        self.assertIn("MemberService", controller)
        self.assertIn("ensureActiveBinding", service)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getUpdatedAt)", ensure_binding)
        self.assertIn("orderByDesc(V2DeviceBindingEntity::getId)", ensure_binding)
        self.assertIn("case \"owner\", \"member\", \"child\"", service)
        self.assertIn("listByDevice", service)
        self.assertIn("V2MemberResponse", service)

    def test_firmware_release_changelog_is_registered(self):
        text = CHANGELOG_MASTER.read_text(encoding="utf-8", errors="replace")

        self.assertIn("id: 202605151720", text)
        self.assertIn("path: classpath:db/changelog/202605151720.sql", text)

    def test_firmware_release_table_supports_m5_rollout_contract(self):
        text = FIRMWARE_RELEASE_SQL.read_text(encoding="utf-8", errors="replace")

        self.assertIn("CREATE TABLE IF NOT EXISTS `firmware_releases`", text)
        for column in (
            "`release_id` VARCHAR(64)",
            "`channel` VARCHAR(32) NOT NULL DEFAULT 'dev'",
            "`version` VARCHAR(32)",
            "`url` VARCHAR(512)",
            "`sha256` CHAR(64)",
            "`signature` TEXT",
            "`rollout_percent` INT NOT NULL DEFAULT 10",
            "`failure_threshold_percent` INT NOT NULL DEFAULT 20",
            "`install_count` INT NOT NULL DEFAULT 0",
            "`failure_count` INT NOT NULL DEFAULT 0",
            "`status` VARCHAR(16) NOT NULL DEFAULT 'draft'",
            "`published_at` DATETIME",
        ):
            self.assertIn(column, text)
        self.assertIn("idx_firmware_releases_channel_status", text)
        self.assertIn("draft|published|paused|retired", text)

    def test_firmware_release_entity_and_dao_exist(self):
        entity = FIRMWARE_RELEASE_ENTITY.read_text(encoding="utf-8", errors="replace")
        dao = FIRMWARE_RELEASE_DAO.read_text(encoding="utf-8", errors="replace")

        self.assertIn('@TableName("firmware_releases")', entity)
        for field in (
            "private String releaseId;",
            "private String channel;",
            "private String version;",
            "private String url;",
            "private String sha256;",
            "private String signature;",
            "private Integer rolloutPercent;",
            "private Integer failureThresholdPercent;",
            "private Integer installCount;",
            "private Integer failureCount;",
            "private String status;",
            "private Date publishedAt;",
        ):
            self.assertIn(field, entity)
        self.assertIn("BaseMapper<V2FirmwareReleaseEntity>", dao)

    def test_firmware_release_service_publishes_dev_plan_and_selects_rollout(self):
        service = FIRMWARE_RELEASE_SERVICE.read_text(encoding="utf-8", errors="replace")

        for token in (
            "CHANNEL_DEV = \"dev\"",
            "STATUS_PUBLISHED = \"published\"",
            "STATUS_PAUSED = \"paused\"",
            "ROLLOUT_10 = 10",
            "ROLLOUT_50 = 50",
            "ROLLOUT_100 = 100",
            "publishDevRelease",
            "findUpgradeForDevice",
            "isDeviceInRollout",
            "MessageDigest.getInstance(\"SHA-256\")",
            "deviceId.getBytes(StandardCharsets.UTF_8)",
            "orderByDesc(V2FirmwareReleaseEntity::getPublishedAt)",
            "orderByDesc(V2FirmwareReleaseEntity::getReleaseId)",
            "StringUtils.equals(currentVersion, release.getVersion())",
            "LOWER_HEX_SHA256",
            "requireHttpsUrl",
            "requireLowerHexSha256",
            "requireBase64Signature",
            "Base64.getDecoder().decode(signature)",
        ):
            self.assertIn(token, service)

    def test_firmware_release_service_auto_pauses_on_failure_rate(self):
        service = FIRMWARE_RELEASE_SERVICE.read_text(encoding="utf-8", errors="replace")

        self.assertIn("recordInstallResult", service)
        self.assertIn("public V2FirmwareReleaseEntity recordInstallResult", service)
        self.assertIn("shouldPauseForFailureRate", service)
        self.assertIn("failureCount * 100 >= installCount * thresholdPercent", service)
        self.assertIn("release.setStatus(STATUS_PAUSED)", service)
        self.assertIn("firmwareReleaseDao.updateById(release)", service)
        self.assertIn("return release", service)

    def test_firmware_release_api_surface_exists_for_publish_and_result_ingest(self):
        controller = FIRMWARE_RELEASE_CONTROLLER.read_text(encoding="utf-8", errors="replace")
        publish_request = FIRMWARE_RELEASE_PUBLISH_REQUEST.read_text(encoding="utf-8", errors="replace")
        result_request = FIRMWARE_INSTALL_RESULT_REQUEST.read_text(encoding="utf-8", errors="replace")
        response = FIRMWARE_RELEASE_RESPONSE.read_text(encoding="utf-8", errors="replace")

        for token in (
            "FirmwareReleaseService",
            '@RequestMapping("/admin/firmware-releases")',
            "@RequiresPermissions(\"sys:role:superAdmin\")",
            "publishFirmwareRelease",
            "firmwareReleaseService.publishDevRelease",
            '@PostMapping("/{releaseId}/install-result")',
            "recordFirmwareInstallResult",
            "firmwareReleaseService.recordInstallResult",
            "Boolean.TRUE.equals(request.getSuccess())",
        ):
            self.assertIn(token, controller)

        for field in (
            "private String releaseId;",
            "private String version;",
            "private String url;",
            "private String sha256;",
            "private String signature;",
            "private Integer rolloutPercent;",
            "private Integer failureThresholdPercent;",
        ):
            self.assertIn(field, publish_request)
        self.assertIn("private Boolean success;", result_request)
        self.assertIn("fromEntity(V2FirmwareReleaseEntity entity)", response)
        self.assertIn("entity.getStatus()", response)
        self.assertIn("entity.getFailureCount()", response)

    def test_internal_firmware_upgrade_plan_bridge_exists(self):
        internal_controller = INTERNAL_CONTROLLER.read_text(encoding="utf-8", errors="replace")
        ota_handler = DEVICE_OTA_HANDLER.read_text(encoding="utf-8", errors="replace")
        http_server = DEVICE_HTTP_SERVER.read_text(encoding="utf-8", errors="replace")

        for token in (
            '"/firmware/upgrade-plan"',
            '"/firmware/install-result"',
            "firmwareReleaseService",
            ".findUpgradeForDevice",
            ".recordInstallResult",
            "V2FirmwareReleaseResponse::fromEntity",
        ):
            self.assertIn(token, internal_controller)

        for token in (
            "firmware_release_business_base_url",
            "/internal/v1/firmware/upgrade-plan",
            "internal_motion_task_token",
            "_fetch_business_firmware_plan",
            "_apply_business_firmware_plan",
            "_forward_business_install_result",
            "/internal/v1/firmware/install-result",
            "release_id",
            '"sha256"',
            '"signature"',
            "_refresh_bin_cache_if_needed",
        ):
            self.assertIn(token, ota_handler)
        self.assertIn("/xiaozhi/ota/install-result", http_server)

    def test_internal_runtime_endpoints_reach_controller_token_auth_before_oauth_filter(self):
        controller = INTERNAL_CONTROLLER.read_text(encoding="utf-8", errors="replace")
        shiro = SHIRO_CONFIG.read_text(encoding="utf-8", errors="replace")

        for endpoint in (
            "/internal/v1/motion_event",
            "/internal/v1/device_info",
            "/internal/v1/self_check",
            "/internal/v1/voice_task",
            "/internal/v1/voiceprints/cache",
            "/internal/v1/firmware/upgrade-plan",
            "/internal/v1/firmware/install-result",
        ):
            with self.subTest(endpoint=endpoint):
                token = f'filterMap.put("{endpoint}", "anon")'
                self.assertIn(token, shiro)
                self.assertLess(shiro.index(token), shiro.index('filterMap.put("/**", "oauth2")'))

        self.assertIn("authorizeInternalRequest", controller)
        self.assertIn('authorization.startsWith("Bearer ")', controller)
        self.assertIn("deviceServerProperties.getInternalToken()", controller)
        self.assertIn("return new Result<Void>().error(ErrorCode.UNAUTHORIZED, \"invalid token\")", controller)

    def test_internal_runtime_controller_authenticates_each_endpoint_before_business_logic(self):
        controller = INTERNAL_CONTROLLER.read_text(encoding="utf-8", errors="replace")
        endpoints = (
            ('@PostMapping("/motion_event")', "appV2Service.ingestMotionEvent(body)"),
            ('@PostMapping("/device_info")', "appV2Service.ingestDeviceInfo(body)"),
            ('@PostMapping("/self_check")', "appV2Service.ingestSelfCheck(body)"),
            ('@PostMapping("/voice_task")', "appV2Service.submitVoiceTask(deviceId, request)"),
            ('@PostMapping("/voiceprints/cache")', "voiceprintEnrollmentService.activeCacheForDevice(deviceId)"),
            ('@PostMapping("/firmware/upgrade-plan")', "firmwareReleaseService"),
            ('@PostMapping("/firmware/install-result")', "firmwareReleaseService.recordInstallResult"),
        )

        for mapping, business_call in endpoints:
            with self.subTest(endpoint=mapping):
                start = controller.index(mapping)
                next_mapping = controller.find("@PostMapping", start + len(mapping))
                next_private = controller.find("private ", start + len(mapping))
                candidates = [pos for pos in (next_mapping, next_private) if pos != -1]
                end = min(candidates) if candidates else len(controller)
                body = controller[start:end]

                self.assertIn("authorizeInternalRequest(authorization", body)
                self.assertIn("if (auth != null)", body)
                self.assertIn(business_call, body)
                self.assertLess(body.index("authorizeInternalRequest(authorization"), body.index(business_call))

    def test_update_gate_rejects_tasks_before_persisting(self):
        service = APP_V2_SERVICE_IMPL.read_text(encoding="utf-8", errors="replace")
        error_codes = SAFETY_ERROR_CODE.read_text(encoding="utf-8", errors="replace")

        self.assertIn("E_DEVICE_UPDATING", error_codes)
        self.assertIn("requireDeviceNotUpdating(normalizedDeviceId)", service)
        self.assertIn("isDeviceUpdating", service)
        self.assertIn('SafetyErrorCode.E_DEVICE_UPDATING', service)
        self.assertIn('"UPDATING"', service)
        self.assertIn('"UPGRADING"', service)

    def test_self_check_history_table_and_service_contract_exist(self):
        master = CHANGELOG_MASTER.read_text(encoding="utf-8", errors="replace")
        sql = SELF_CHECK_HISTORY_SQL.read_text(encoding="utf-8", errors="replace")
        entity = SELF_CHECK_EVENT_ENTITY.read_text(encoding="utf-8", errors="replace")
        dao = SELF_CHECK_EVENT_DAO.read_text(encoding="utf-8", errors="replace")
        response = SELF_CHECK_HISTORY_RESPONSE.read_text(encoding="utf-8", errors="replace")
        controller = APP_V2_CONTROLLER.read_text(encoding="utf-8", errors="replace")
        app_service = APP_V2_SERVICE_IMPL.read_text(encoding="utf-8", errors="replace")

        self.assertIn("id: 202605160145", master)
        self.assertIn("path: classpath:db/changelog/202605160145.sql", master)
        self.assertIn("CREATE TABLE IF NOT EXISTS `device_self_check_events`", sql)
        for column in (
            "`device_id` VARCHAR(64) NOT NULL",
            "`check_id` VARCHAR(64)",
            "`scope` VARCHAR(32)",
            "`status` VARCHAR(16)",
            "`summary` VARCHAR(512)",
            "`checks_json` TEXT",
            "`payload_json` TEXT",
            "`reported_at` DATETIME",
            "idx_self_check_device_reported",
        ):
            self.assertIn(column, sql)
        self.assertIn('@TableName("device_self_check_events")', entity)
        self.assertIn("@TableId(type = IdType.AUTO)", entity)
        self.assertIn("BaseMapper<V2DeviceSelfCheckEventEntity>", dao)
        self.assertIn("fromEntity(V2DeviceSelfCheckEventEntity entity)", response)
        self.assertIn("/devices/{deviceId}/self-check/history", controller)
        self.assertIn("listSelfCheckHistory", controller)
        self.assertIn("persistSelfCheck(payload)", app_service)
        self.assertIn("v2DeviceSelfCheckEventDao.insert(event)", app_service)
        self.assertIn("orderByDesc(V2DeviceSelfCheckEventEntity::getReportedAt)", app_service)
        self.assertIn('.last("limit 5")', app_service)
        self.assertLess(app_service.index("requireDeviceNotUpdating(normalizedDeviceId)"), app_service.index("v2TaskDao.insert(task)"))

    def test_self_check_ingest_and_edge_a_event_exist(self):
        service = APP_V2_SERVICE_IMPL.read_text(encoding="utf-8", errors="replace")
        controller = INTERNAL_CONTROLLER.read_text(encoding="utf-8", errors="replace")
        hub = (
            ROOT
            / "server"
            / "xiaozhi-esp32-server"
            / "main"
            / "manager-api"
            / "src"
            / "main"
            / "java"
            / "xiaozhi"
            / "modules"
            / "appv2"
            / "ws"
            / "EdgeAClientHub.java"
        ).read_text(encoding="utf-8", errors="replace")

        self.assertIn('/self_check', controller)
        self.assertIn("ingestSelfCheck", service)
        self.assertIn("publishSelfCheck", service + hub)
        self.assertIn('"self_check"', hub)
        self.assertIn('"checks"', hub)


if __name__ == "__main__":
    unittest.main()
