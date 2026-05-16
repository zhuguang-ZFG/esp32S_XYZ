# Edge-A（Client ↔ BusinessServer）WSS 文本帧契约

与 `docs/架构定稿-v2.md` §9.1–§9.2 对齐；实现入口：`manager-api` 路径 **`/ws/v1/client`**（若部署 `context-path=/xiaozhi`，则完整 URL 为 `/xiaozhi/ws/v1/client`）。

## 鉴权（§9.1）

连接建立后**首条**（或首条之一）客户端消息必须为 `auth`，否则服务端可关闭连接。

```json
{ "op": "auth", "token": "<与 HTTP Authorization Bearer 相同的 JWT>" }
```

JWT 校验与既有 `SysUserTokenService.getUserByToken` 一致（智控台登录 token）。

## 客户端 → 服务端（§9.2）

| `op` | 字段 | 说明 |
|------|------|------|
| `auth` | `token` | JWT |
| `subscribe_device` | `device_id`, `req_id`（可选）, `since_seq`（可选） | 订阅 `device:{device_id}` |
| `subscribe_task` | `task_id`, `req_id`（可选） | 订阅 `task:{task_id}` |
| `unsubscribe` | `topic` | 取消订阅 |
| `ack` | `topic`, `seq` | 确认已收到 seq，服务端返回 `acked` |
| `ping` | — | 心跳 |

## 服务端 → 客户端（§9.2）

| `type` | 说明 |
|--------|------|
| `authed` | `{ "type":"authed","user_id": 10001 }` |
| `subscribed` | `{ "type":"subscribed","topic":"device:dev_xxx","since_seq": <long> }` |
| `unsubscribed` | `{ "type":"unsubscribed","topic":"device:dev_xxx" }` |
| `acked` | `{ "type":"acked","topic":"device:dev_xxx","seq": <long> }` |
| `event` | `{ "type":"event","event": { ... §6.2 } }`；M2.7 由 `motion_event` ingest 触发 `job_status`，M2.13 由 `device_info` ingest 触发 `device_info_reply` |
| `pong` | 响应 `ping` |
| `error` | `{ "type":"error","code":"E_AUTH|E_NOT_FOUND|E_FORBIDDEN", "message":"..." }` |

## M2.7 与后续里程碑

- **M2.7**：内存 `seq` 单调（每设备）、**不实现** §9.3 断线窗口回灌（归 **M2.10**）。
- **M2b / M2.9**：事件落库与 `since_seq` 重放语义以实施计划为准。

## 契约完成度

已落地 14/14 schema + 15 示例：`auth`, `subscribe_device`, `subscribe_task`, `unsubscribe`, `ack`, `ping`（客户端）；`authed`, `subscribed`, `unsubscribed`, `acked`, `event(job_status)`, `event(job_status.progress)`, `event(device_info_reply)`, `pong`, `error`（服务端）。

示例见 `examples/` 目录。
