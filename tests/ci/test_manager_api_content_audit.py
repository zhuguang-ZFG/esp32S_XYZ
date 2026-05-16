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
CONTENT_AUDIT_SQL = CHANGELOG_MASTER.with_name("202605151545.sql")
CONTENT_AUDIT_ENTITY = (
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
    / "V2ContentAuditEntity.java"
)
CONTENT_AUDIT_SERVICE = (
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
    / "contentaudit"
    / "ContentAuditLogService.java"
)
DRAW_GENERATED_PROJECTION_SERVICE = (
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
    / "DrawGeneratedProjectionService.java"
)
DRAW_GENERATED_SVG_PROVIDER = DRAW_GENERATED_PROJECTION_SERVICE.with_name("DrawGeneratedSvgProvider.java")
LOCAL_FAKE_DRAW_GENERATED_SVG_PROVIDER = DRAW_GENERATED_PROJECTION_SERVICE.with_name(
    "LocalFakeDrawGeneratedSvgProvider.java"
)
STARTER_ASSET_CATALOG = DRAW_GENERATED_PROJECTION_SERVICE.with_name("StarterAssetCatalog.java")
APP_V2_SERVICE = (
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


class ManagerApiContentAuditTests(unittest.TestCase):
    def test_content_audit_changelog_is_registered(self):
        text = CHANGELOG_MASTER.read_text(encoding="utf-8", errors="replace")

        self.assertIn("id: 202605151545", text)
        self.assertIn("path: classpath:db/changelog/202605151545.sql", text)

    def test_content_audit_table_stores_metadata_only(self):
        text = CONTENT_AUDIT_SQL.read_text(encoding="utf-8", errors="replace")

        self.assertIn("CREATE TABLE IF NOT EXISTS `content_audit`", text)
        for column in (
            "`account_id` BIGINT",
            "`device_id` VARCHAR(64)",
            "`path` VARCHAR(64)",
            "`raw_hash` CHAR(64)",
            "`rule_hit` VARCHAR(255)",
            "`ts` DATETIME",
        ):
            self.assertIn(column, text)
        self.assertIn("idx_content_audit_device_ts", text)
        self.assertIn("idx_content_audit_account_ts", text)
        self.assertIn("idx_content_audit_path_ts", text)
        self.assertNotIn("raw_text", text)
        self.assertNotIn("content_text", text)

    def test_content_audit_entity_and_service_use_hash_not_raw_text(self):
        entity = CONTENT_AUDIT_ENTITY.read_text(encoding="utf-8", errors="replace")
        service = CONTENT_AUDIT_SERVICE.read_text(encoding="utf-8", errors="replace")

        self.assertIn('@TableName("content_audit")', entity)
        self.assertIn("private String rawHash;", entity)
        self.assertNotIn("rawText", entity)
        self.assertNotIn("contentText", entity)
        self.assertIn('MessageDigest.getInstance("SHA-256")', service)
        self.assertIn("audit.setRawHash(sha256Hex", service)
        self.assertIn("private static final int RETENTION_DAYS = 180", service)
        self.assertIn("purgeExpired", service)

    def test_draw_generated_text_inputs_are_content_audited(self):
        text = APP_V2_SERVICE.read_text(encoding="utf-8", errors="replace")

        self.assertIn('auditInboundTextField("write_text.text"', text)
        self.assertIn('auditInboundTextField("draw_generated.prompt"', text)
        self.assertIn('auditInboundTextField("draw_generated.svg_text"', text)
        self.assertIn('auditInboundTextField("draw_generated.transcript"', text)
        self.assertIn("new ContentAuditException(e.getRuleHit(), path, raw)", text)
        self.assertIn("error.getAuditPath()", text)
        self.assertIn("error.getAuditRaw()", text)

    def test_draw_generated_prompt_has_deterministic_projection_fallback(self):
        projection = DRAW_GENERATED_PROJECTION_SERVICE.read_text(encoding="utf-8", errors="replace")
        provider = LOCAL_FAKE_DRAW_GENERATED_SVG_PROVIDER.read_text(encoding="utf-8", errors="replace")
        provider_interface = DRAW_GENERATED_SVG_PROVIDER.read_text(encoding="utf-8", errors="replace")

        self.assertIn("interface DrawGeneratedSvgProvider", provider_interface)
        self.assertIn("GeneratedSvg generate", provider_interface)
        self.assertIn("DrawGeneratedSvgProvider svgProvider", projection)
        self.assertIn("svgProvider.generate(prompt, params)", projection)
        self.assertIn('"draw_generated_prompt_placeholder_v1"', provider)
        self.assertIn('"local_fake_ai"', provider)
        self.assertIn("promptPlaceholderSvg(prompt)", provider)
        self.assertIn('fill=\\"none\\" stroke=\\"black\\"', provider)

    def test_draw_generated_bitmap_inputs_are_vectorized_before_projection(self):
        text = DRAW_GENERATED_PROJECTION_SERVICE.read_text(encoding="utf-8", errors="replace")

        self.assertIn('"draw_generated_bitmap_vectorize_minimal_v1"', text)
        self.assertIn('firstStringParam(params, "bitmap_base64", "bitmap_data_uri", "image_base64")', text)
        self.assertIn("ImageIO.read", text)
        self.assertIn("isDarkPixel", text)
        self.assertIn('source = "bitmap"', text)
        self.assertIn('"bitmap_too_complex"', text)

    def test_draw_generated_projection_uses_device_writable_layout_defaults(self):
        text = APP_V2_SERVICE.read_text(encoding="utf-8", errors="replace")

        self.assertIn("buildDrawGeneratedDispatchRequest", text)
        self.assertIn("withWritableLayoutDefaults", text)
        self.assertIn("caps.writableBounds()", text)
        self.assertIn('resolved.put("canvas_width_mm"', text)
        self.assertIn('resolved.put("canvas_height_mm"', text)
        self.assertIn('resolved.putIfAbsent("origin_x_mm"', text)
        self.assertIn('resolved.putIfAbsent("origin_y_mm"', text)

    def test_draw_generated_supports_structured_layout_hints(self):
        app_service = APP_V2_SERVICE.read_text(encoding="utf-8", errors="replace")
        projection_service = DRAW_GENERATED_PROJECTION_SERVICE.read_text(encoding="utf-8", errors="replace")

        self.assertIn("layoutScaleHint", app_service)
        self.assertIn('"size_hint"', app_service)
        self.assertIn('"larger"', app_service)
        self.assertIn('"smaller"', app_service)
        self.assertIn('"more_margin"', app_service)
        self.assertIn("layoutAlign", projection_service)
        self.assertIn('"layout_hint"', projection_service)
        self.assertIn("TOP_LEFT", projection_service)
        self.assertIn("BOTTOM_RIGHT", projection_service)

    def test_draw_generated_only_uses_starter_assets_when_explicit(self):
        projection = DRAW_GENERATED_PROJECTION_SERVICE.read_text(encoding="utf-8", errors="replace")
        catalog = STARTER_ASSET_CATALOG.read_text(encoding="utf-8", errors="replace")

        self.assertIn("starterAssetParam", projection)
        self.assertIn('"starter_id", "starter_asset_id", "preset_id"', projection)
        self.assertIn('"use_starter_asset"', projection)
        self.assertIn('"starter_asset_not_explicit"', projection)
        self.assertIn('"starter_asset_not_found"', projection)
        self.assertIn('"draw_generated_starter_asset_v1"', catalog)
        for asset_id in ("starter_star", "starter_house", "starter_tree", "starter_fish", "starter_flower"):
            self.assertIn(asset_id, catalog)


if __name__ == "__main__":
    unittest.main()
