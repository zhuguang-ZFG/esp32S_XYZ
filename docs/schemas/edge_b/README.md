# Edge-B（BusinessServer ↔ DeviceServer）HTTP 契约

> ⚠️ 历史归档：本契约描述的 `BusinessServer` / `DeviceServer` 服务端组件已于 2026-06-25 物理删除，能力迁移至 LiMa 主项目 `device_gateway`。
> 当前任务下发、运动事件/设备信息上行的统一入口为 LiMa 主项目 `routes/device_gateway.py: WS /device/v1/ws` 与 `routes/device_gateway_events_routes.py: POST /device/v1/events`。
> 下方内容保留为历史参考。

与 `docs/架构定稿-v2.md` §15.2 对齐：

- **鉴权**：`Authorization: Bearer <internal-token>`，与 yml 配置 `server.internal_motion_task_token` / `v2.device-server.internal-token` 一致。
- **方法 / 路径**：

| 方向 | 路径 | 用途 |
|------|------|------|
| BusinessServer → DeviceServer | `POST /internal/v1/motion_task` | 任务下发 |
| DeviceServer → BusinessServer | `POST /internal/v1/motion_event` | 任务事件上行（M2.6） |
| DeviceServer → BusinessServer | `POST /internal/v1/intent_submit` | 语音意图提交（M4.3） |
| DeviceServer → BusinessServer | `POST /internal/v1/device_info` | 设备信息上报（M2.13） |

## 响应格式

与 BusinessServer 统一 `Result` 包装一致：
- **200**：`{ "code": 0, "msg": "success", "data": ... }`
- **4xx/5xx**：`{ "code": <error_code>, "msg": "<message>" }`

## 完成度

已落地 3 个 schema：`motion_task`, `motion_event`, `intent_submit`。
`motion_task` 示例覆盖 `home` 与 `run_path`；`motion_event` 示例覆盖 `running` 与 `progress`。
`device_info` 上报走 Edge-C `device_info` WSS 帧与 BusinessServer `/internal/v1/device_info` 入口，当前不在 Edge-B schema 目录单独建模。

示例见 `examples/` 目录。
