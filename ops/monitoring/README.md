# M6 Monitoring

Minimal Prometheus/Grafana/Alertmanager assets for the v2 runtime.

Local/staging compose entry:

```powershell
rtk docker compose -f ops/monitoring/docker-compose.yml up
```

The compose file starts Prometheus on `9090`, Alertmanager on `9093`, and Grafana on `3000`. It expects manager-api to be reachable as `manager-api:8002` on the `m6-monitoring` network, or the Prometheus target must be adapted in deployment.

Prometheus scrapes Manager API through the server-secret filter with the same Bearer server secret used by internal `/config/**` calls. The scrape config uses `authorization.credentials_file` at `/etc/prometheus/secrets/manager-api.secret`. Before starting the local stack, create `ops/monitoring/secrets/manager-api.secret` with the staging `server.secret` value, or set `MANAGER_API_SECRET_FILE` to an existing secret file. Do not commit real secret files; `ops/monitoring/secrets/.gitignore` keeps local secret files out of git.

Manager API exposes Prometheus text metrics at:

```text
GET /xiaozhi/internal/v1/metrics
Authorization: Bearer <server.secret>
```

Alert rules covered:

- `ManagerApiTaskFailureRateHigh`: `biz_task_failure_ratio > 0.05` for 5 minutes.
- `DeviceOfflineSpike`: `dev_device_offline_total > 0` for 5 minutes.
- `OtaRolloutFailureRateHigh`: `u8_ota_failure_ratio > 0.03` for 2 minutes.

OTA rollout counters aggregate firmware releases with `active`, `published`, or `paused` status. Draft and retired releases are excluded from this local alert ratio.

Dashboard-only visibility:

- device lifecycle counts: bound, RMA, returned, and disposed devices.
- product notification outbox counts:
  - `product_notification_pending_total`
  - `product_notification_failed_total`

Product notification metrics are local pre-send queue visibility only. Failed rows are expected to be marked by future push delivery code through `ProductNotificationOutboxService.markProviderFailed(...)`. These metrics do not prove platform push, native tabBar rendering, or background reminder delivery.

Alertmanager webhook placeholders:

- `DINGTALK_WEBHOOK_URL`
- `WECHAT_WEBHOOK_URL`

Do not commit real webhook URLs. The compose file provides inert placeholder URLs when the environment variables are missing.
