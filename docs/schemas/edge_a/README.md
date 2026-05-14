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
| `ack` | `ack_seq` | M2.7 占位接收，可不处理 |
| `ping` | — | 心跳 |

## 服务端 → 客户端（§9.2）

| `type` | 说明 |
|--------|------|
| `subscribed` | `{ "type":"subscribed","topic":"device:dev_xxx","since_seq": <long> }` |
| `event` | `{ "type":"event","event": { ... §6.2 } }`；M2.7 由 `motion_event` ingest 触发，`event_type` 使用 `job_status`，`payload` 含 `phase`/`capability` |
| `pong` | 响应 `ping` |
| `error` | `{ "type":"error","code":"E_AUTH|E_NOT_FOUND|E_FORBIDDEN", "message":"..." }` |

## M2.7 与后续里程碑

- **M2.7**：内存 `seq` 单调（每设备）、**不实现** §9.3 断线窗口回灌（归 **M2.10**）。
- **M2b / M2.9**：事件落库与 `since_seq` 重放语义以实施计划为准。

示例见 `examples/` 目录。
