# M6 Monitoring Alerts Runbook

Date: 2026-05-16

## Scope

This runbook covers the deployment-facing checks for the minimal M6 monitoring slice:

- manager-api Prometheus text endpoint:
  - `GET /xiaozhi/internal/v1/metrics`
- Prometheus scrape config:
  - `ops/monitoring/prometheus.yml`
  - scrape target `manager-api:8002`
  - metrics path `/xiaozhi/internal/v1/metrics`
  - Bearer server-secret credentials file `/etc/prometheus/secrets/manager-api.secret`
- Alert rules:
  - `ManagerApiTaskFailureRateHigh`
  - `DeviceOfflineSpike`
  - `OtaRolloutFailureRateHigh`
- Alertmanager webhook placeholders:
  - `DINGTALK_WEBHOOK_URL`
  - `WECHAT_WEBHOOK_URL`
- Grafana dashboard provisioning:
  - `ops/monitoring/grafana/provisioning/dashboards/json/manager-api-runtime.json`
- Local/staging compose entry:
  - `ops/monitoring/docker-compose.yml`

## Pre-Deployment Checks

Run from the repository root:

```powershell
rtk python -m unittest tests.ci.test_monitoring_alerts -v
```

Run from `server/xiaozhi-esp32-server/main/manager-api`:

```powershell
rtk mvn "-Dtest=MonitoringMetricsServiceImplTest,MonitoringMetricsControllerTest" test
```

Confirm the metric endpoint is reachable in the target environment. Run this command from the repository root for local/staging smoke checks, or replace `ops/monitoring/secrets/manager-api.secret` with the deployment-managed secret file path:

```powershell
rtk powershell.exe -NoProfile -Command "$secret = Get-Content 'ops/monitoring/secrets/manager-api.secret' -Raw; Invoke-WebRequest -UseBasicParsing 'http://manager-api:8002/xiaozhi/internal/v1/metrics' -Headers @{ Authorization = 'Bearer ' + $secret.Trim() } | Select-Object -ExpandProperty Content"
```

Expected metric names:

- `biz_task_failure_ratio`
- `dev_device_offline_total`
- `u8_ota_failure_ratio`
- `dev_device_rma_in_progress_total`
- `dev_device_returned_total`
- `dev_device_disposed_total`
- `product_notification_pending_total`
- `product_notification_failed_total`

## Deployment Configuration

Prometheus must load:

- `ops/monitoring/prometheus.yml`
- `ops/monitoring/rules/manager-api-alerts.yml`

Prometheus must authenticate to Manager API with the internal server secret:

- create `ops/monitoring/secrets/manager-api.secret` for local/staging smoke runs, or set `MANAGER_API_SECRET_FILE` to the deployment-managed secret file
- configure `authorization.credentials_file: /etc/prometheus/secrets/manager-api.secret`
- never commit the secret value; `ops/monitoring/secrets/.gitignore` keeps local secret files out of git

For a local or staging smoke run, start the monitoring stack from the repository root:

```powershell
rtk docker compose -f ops/monitoring/docker-compose.yml up
```

The compose stack exposes Prometheus on `9090`, Alertmanager on `9093`, and Grafana on `3000`. Confirm manager-api is reachable as `manager-api:8002` on the `m6-monitoring` network, or adapt the Prometheus target in the deployment-specific config. If the secret file lives outside `ops/monitoring/secrets`, set `MANAGER_API_SECRET_FILE` before running compose.

Alertmanager must load webhook URLs from deployment secrets:

- `DINGTALK_WEBHOOK_URL`
- `WECHAT_WEBHOOK_URL`

Do not commit real webhook URLs. Deployment evidence should include secret names or redacted rendered config, not secret values.

Grafana must mount:

- `ops/monitoring/grafana/provisioning/datasources/prometheus.yml`
- `ops/monitoring/grafana/provisioning/dashboards/dashboards.yml`
- `ops/monitoring/grafana/provisioning/dashboards/json/manager-api-runtime.json`

## Alert Drill

Use a non-production or controlled staging environment for the first drill.

1. Confirm Prometheus target health shows `manager-api` as up and is not failing with a 401 from the server-secret filter.
2. Confirm the three alert rules are loaded.
3. Trigger or temporarily simulate each metric crossing its threshold:
   - `biz_task_failure_ratio > 0.05` for 5 minutes
   - `dev_device_offline_total > 0` for 5 minutes
   - `u8_ota_failure_ratio > 0.03` for 2 minutes
4. Confirm Alertmanager receives the alert.
5. Confirm DingTalk or WeCom receives the notification.
6. Confirm resolved notifications are sent because `send_resolved: true` is configured.

Attach screenshots or exported logs to the release ticket:

- Prometheus target health
- Prometheus rules page
- Alertmanager alert view
- DingTalk or WeCom notification
- Grafana dashboard showing the metric
- Grafana device lifecycle panel showing bound, RMA, returned, and disposed counts
- Grafana product notification outbox panel showing pending and failed local pre-send rows

## Monitoring Evidence Gap Record

Use this structure when staging or production monitoring evidence is missing for a release candidate. It is not evidence of deployed alerting.

- missing evidence scope: scrape health, secret mount, 401 refusal, dashboard rendering, Alertmanager route, DingTalk delivery, or WeCom delivery
- environment: staging, production, or named release-candidate environment
- owner: named operations owner accountable for closing the gap
- due date: calendar date for closing the missing evidence
- fallback path: manual Manager API metrics smoke, manager-api logs, database health checks, and release hold for untriaged alerts
- risk acceptance: technical owner and operations owner approval reference
- rollback trigger: failed scrape auth, missing dashboard, missing alert route, or missing webhook delivery during the release window
- follow-up evidence: redacted scrape config, redacted secret mount proof, 401 refusal output, Prometheus target screenshot, Grafana dashboard screenshot, Alertmanager route screenshot, DingTalk/WeCom delivery screenshot, and resolved-notification evidence

## Triage Guidance

`ManagerApiTaskFailureRateHigh`:

- Check recent `tasks.status = failed` rows by capability and device.
- Check manager-api logs around task submission and motion event ingestion.
- Pause risky rollout paths if failures cluster around a new capability.

`DeviceOfflineSpike`:

- Check device heartbeat and `last_seen_at`.
- Check DeviceServer connectivity and WebSocket auth errors.
- Compare with known maintenance windows or network incidents.

`OtaRolloutFailureRateHigh`:

- Stop further OTA rollout waves.
- Check firmware release id, device model, and failure reason distribution.
- Interpret the ratio as active, published, and paused firmware releases only; draft and retired releases are excluded.
- Confirm rollback image and update gate status before resuming.

Product notification outbox metrics:

- `product_notification_pending_total` tracks local pre-send rows waiting for a future push worker.
- `product_notification_failed_total` tracks local rows marked failed by future push delivery code through `ProductNotificationOutboxService.markProviderFailed(...)`.
- These metrics are operational visibility only; they do not prove platform push, native tabBar rendering, or background reminder delivery.

## Current Local Limitation

CI verifies monitoring files and Java metric generation statically, including the server-secret scrape contract. It does not start Prometheus, Grafana, or Alertmanager, and it does not prove webhook delivery. Production or staging deployment evidence must be attached after the monitoring stack is started.
