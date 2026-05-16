import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OPS = ROOT / "ops" / "monitoring"
API = ROOT / "server" / "xiaozhi-esp32-server" / "main" / "manager-api"

PROM = OPS / "prometheus.yml"
RULES = OPS / "rules" / "manager-api-alerts.yml"
ALERTMANAGER = OPS / "alertmanager.yml"
COMPOSE = OPS / "docker-compose.yml"
DASHBOARD = OPS / "grafana" / "provisioning" / "dashboards" / "json" / "manager-api-runtime.json"
SECRETS_GITIGNORE = OPS / "secrets" / ".gitignore"
RUNBOOK = ROOT / "ops" / "runbooks" / "m6-monitoring-alerts.md"
README = OPS / "README.md"
STATUS = ROOT / "docs" / "M6.8-monitoring-alerts-status.md"
CONTROLLER = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "controller" / "MonitoringMetricsController.java"
SERVICE = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "impl" / "MonitoringMetricsServiceImpl.java"
FIRMWARE_SERVICE = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "appv2" / "service" / "firmware" / "FirmwareReleaseService.java"
SHIRO_CONFIG = API / "src" / "main" / "java" / "xiaozhi" / "modules" / "security" / "config" / "ShiroConfig.java"


class MonitoringAlertsTest(unittest.TestCase):
    def test_manager_api_metrics_endpoint_exists(self):
        controller = CONTROLLER.read_text(encoding="utf-8", errors="replace")
        service = SERVICE.read_text(encoding="utf-8", errors="replace")
        shiro = SHIRO_CONFIG.read_text(encoding="utf-8", errors="replace")

        self.assertIn('"/metrics"', controller)
        self.assertIn("MediaType.TEXT_PLAIN_VALUE", controller)
        self.assertIn('filterMap.put("/internal/v1/metrics", "server")', shiro)
        self.assertLess(shiro.index('filterMap.put("/internal/v1/metrics", "server")'), shiro.index('filterMap.put("/**", "oauth2")'))
        self.assertIn("biz_task_failure_ratio", service)
        self.assertIn("dev_device_offline_total", service)
        self.assertIn("u8_ota_failure_ratio", service)
        self.assertIn("dev_device_rma_in_progress_total", service)
        self.assertIn("dev_device_returned_total", service)
        self.assertIn("dev_device_disposed_total", service)
        self.assertIn("product_notification_pending_total", service)
        self.assertIn("product_notification_failed_total", service)
        self.assertIn('countDevicesByStatus("rma_in_progress")', service)
        self.assertIn('countDevicesByStatus("returned")', service)
        self.assertIn('countDevicesByStatus("disposed")', service)
        self.assertIn("V2ProductNotificationEventDao", service)
        self.assertIn("countNotificationEvents(NOTIFICATION_STATUS_PENDING)", service)
        self.assertIn("countNotificationEvents(NOTIFICATION_STATUS_FAILED)", service)

    def test_ota_failure_metric_is_backed_by_firmware_rollout_counters(self):
        metrics_service = SERVICE.read_text(encoding="utf-8", errors="replace")
        firmware_service = FIRMWARE_SERVICE.read_text(encoding="utf-8", errors="replace")

        for token in (
            "V2FirmwareReleaseDao",
            'sumFirmwareInteger("install_count")',
            'sumFirmwareInteger("failure_count")',
            "V2FirmwareReleaseEntity::getStatus",
            "RELEASE_STATUS_PUBLISHED",
            "RELEASE_STATUS_PAUSED",
            "Firmware rollout failure ratio across active, published, and paused releases.",
            "u8_ota_failure_ratio",
        ):
            self.assertIn(token, metrics_service)
        self.assertIn("recordInstallResult", firmware_service)
        self.assertIn("release.setStatus(STATUS_PAUSED)", firmware_service)

    def test_prometheus_scrapes_manager_api(self):
        text = PROM.read_text(encoding="utf-8", errors="replace")
        secrets_gitignore = SECRETS_GITIGNORE.read_text(encoding="utf-8", errors="replace")

        self.assertIn("metrics_path: /xiaozhi/internal/v1/metrics", text)
        self.assertIn("authorization:", text)
        self.assertIn("type: Bearer", text)
        self.assertIn("credentials_file: /etc/prometheus/secrets/manager-api.secret", text)
        self.assertIn("manager-api:8002", text)
        self.assertIn("rules/manager-api-alerts.yml", text)
        self.assertIn("*", secrets_gitignore)
        self.assertIn("!.gitignore", secrets_gitignore)

    def test_monitoring_compose_starts_core_stack_with_placeholder_secrets(self):
        text = COMPOSE.read_text(encoding="utf-8", errors="replace")

        for token in (
            "prom/prometheus:v2.54.1",
            "prom/alertmanager:v0.27.0",
            "grafana/grafana:11.1.0",
            "./prometheus.yml:/etc/prometheus/prometheus.yml:ro",
            "./rules:/etc/prometheus/rules:ro",
            "MANAGER_API_SECRET_FILE",
            "/etc/prometheus/secrets/manager-api.secret:ro",
            "./alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro",
            "./grafana/provisioning:/etc/grafana/provisioning:ro",
            "--config.expand-env",
            "DINGTALK_WEBHOOK_URL",
            "WECHAT_WEBHOOK_URL",
            "m6-monitoring",
            "\"9090:9090\"",
            "\"9093:9093\"",
            "\"3000:3000\"",
        ):
            self.assertIn(token, text)

    def test_monitoring_readme_documents_dashboard_only_outbox_visibility(self):
        text = README.read_text(encoding="utf-8", errors="replace")

        self.assertIn("Dashboard-only visibility", text)
        self.assertIn("ops/monitoring/docker-compose.yml", text)
        self.assertIn("manager-api:8002", text)
        self.assertIn("m6-monitoring", text)
        self.assertIn("Authorization: Bearer <server.secret>", text)
        self.assertIn("ops/monitoring/secrets/manager-api.secret", text)
        self.assertIn("MANAGER_API_SECRET_FILE", text)
        self.assertIn("Do not commit real secret files", text)
        self.assertIn("product_notification_pending_total", text)
        self.assertIn("product_notification_failed_total", text)
        self.assertIn("local pre-send queue visibility only", text)
        self.assertIn("ProductNotificationOutboxService.markProviderFailed", text)
        self.assertIn("server-secret filter", text)
        self.assertIn("MANAGER_API_SECRET_FILE", text)
        self.assertIn("authorization.credentials_file", text)
        self.assertIn("ops/monitoring/secrets/.gitignore", text)
        self.assertIn("/etc/prometheus/secrets/manager-api.secret", text)
        self.assertIn("do not prove platform push, native tabBar rendering, or background reminder delivery", text)
        self.assertNotIn("system badge", text)
        self.assertIn("active", text)
        self.assertIn("published", text)
        self.assertIn("paused", text)
        self.assertIn("Draft and retired releases are excluded", text)

    def test_monitoring_status_documents_ota_rollout_counter_scope(self):
        text = STATUS.read_text(encoding="utf-8", errors="replace")

        self.assertIn("active", text)
        self.assertIn("published", text)
        self.assertIn("paused", text)
        self.assertIn("retired and draft releases are excluded", text)
        self.assertIn("ProductNotificationOutboxService.markProviderFailed", text)
        self.assertIn("server-secret filter", text)
        self.assertIn("MANAGER_API_SECRET_FILE", text)

    def test_required_alerts_and_channels_exist(self):
        rules = RULES.read_text(encoding="utf-8", errors="replace")
        alertmanager = ALERTMANAGER.read_text(encoding="utf-8", errors="replace")

        self.assertIn("ManagerApiTaskFailureRateHigh", rules)
        self.assertIn("biz_task_failure_ratio > 0.05", rules)
        self.assertIn("DeviceOfflineSpike", rules)
        self.assertIn("dev_device_offline_total > 0", rules)
        self.assertIn("OtaRolloutFailureRateHigh", rules)
        self.assertIn("u8_ota_failure_ratio > 0.03", rules)
        self.assertIn("DINGTALK_WEBHOOK_URL", alertmanager)
        self.assertIn("WECHAT_WEBHOOK_URL", alertmanager)

    def test_grafana_dashboard_tracks_three_core_panels(self):
        dashboard = json.loads(DASHBOARD.read_text(encoding="utf-8", errors="replace"))
        titles = {panel["title"] for panel in dashboard["panels"]}
        exprs = {
            target["expr"]
            for panel in dashboard["panels"]
            for target in panel.get("targets", [])
        }

        self.assertIn("Task Failure Ratio", titles)
        self.assertIn("Offline Devices", titles)
        self.assertIn("OTA Failure Ratio", titles)
        self.assertIn("Device Lifecycle Counts", titles)
        self.assertIn("Product Notification Outbox", titles)
        self.assertIn("biz_task_failure_ratio", exprs)
        self.assertIn("dev_device_offline_total", exprs)
        self.assertIn("u8_ota_failure_ratio", exprs)
        self.assertIn("dev_device_rma_in_progress_total", exprs)
        self.assertIn("dev_device_returned_total", exprs)
        self.assertIn("dev_device_disposed_total", exprs)
        self.assertIn("product_notification_pending_total", exprs)
        self.assertIn("product_notification_failed_total", exprs)

    def test_monitoring_runbook_covers_deployment_and_alert_drill(self):
        text = RUNBOOK.read_text(encoding="utf-8", errors="replace")

        for token in (
            "GET /xiaozhi/internal/v1/metrics",
            "ops/monitoring/prometheus.yml",
            "ops/monitoring/docker-compose.yml",
            "ops/monitoring/rules/manager-api-alerts.yml",
            "Bearer server-secret credentials file",
            "ManagerApiTaskFailureRateHigh",
            "DeviceOfflineSpike",
            "OtaRolloutFailureRateHigh",
            "DINGTALK_WEBHOOK_URL",
            "WECHAT_WEBHOOK_URL",
            "dev_device_rma_in_progress_total",
            "dev_device_returned_total",
            "dev_device_disposed_total",
            "product_notification_pending_total",
            "product_notification_failed_total",
            "local pre-send rows waiting for a future push worker",
            "ProductNotificationOutboxService.markProviderFailed",
            "do not prove platform push, native tabBar rendering, or background reminder delivery",
            "active, published, and paused firmware releases only",
            "draft and retired releases are excluded",
            "rtk python -m unittest tests.ci.test_monitoring_alerts -v",
            "rtk mvn",
            "rtk docker compose -f ops/monitoring/docker-compose.yml up",
            "Run this command from the repository root for local/staging smoke checks",
            "replace `ops/monitoring/secrets/manager-api.secret` with the deployment-managed secret file path",
            "start the monitoring stack from the repository root",
            "Alert Drill",
            "401 from the server-secret filter",
            "send_resolved: true",
            "Monitoring Evidence Gap Record",
            "Use this structure when staging or production monitoring evidence is missing",
            "missing evidence scope: scrape health, secret mount, 401 refusal, dashboard rendering, Alertmanager route, DingTalk delivery, or WeCom delivery",
            "fallback path: manual Manager API metrics smoke, manager-api logs, database health checks, and release hold for untriaged alerts",
            "risk acceptance: technical owner and operations owner approval reference",
            "rollback trigger: failed scrape auth, missing dashboard, missing alert route, or missing webhook delivery during the release window",
            "follow-up evidence: redacted scrape config, redacted secret mount proof, 401 refusal output, Prometheus target screenshot, Grafana dashboard screenshot, Alertmanager route screenshot, DingTalk/WeCom delivery screenshot, and resolved-notification evidence",
            "does not start Prometheus, Grafana, or Alertmanager",
            "does not prove webhook delivery",
        ):
            self.assertIn(token, text)


if __name__ == "__main__":
    unittest.main()
