# Edge-C（DeviceServer ↔ U8）WSS 文本帧契约

与 `docs/架构定稿-v2.md` §15.3 对齐：在既有小智 WSS JSON 文本通道上增加 `motion_task`（下行）与 `motion_event`（上行）。

## M2.6 最小上行：`motion_event`（相位）

**范围**：本里程碑只要求任务生命周期相位，不含 §15.2 全量事件信封中的 `seq` / `event_id` / `payload` 嵌套；全量字段在 **M2b** 固化。

### 设备 → DeviceServer（WSS 文本 JSON）

根对象字段（与现有会话帧一致）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `session_id` | string | 是 | 由 `Protocol` 注入，与当前音频通道会话一致 |
| `type` | string | 是 | 固定为 `motion_event` |
| `task_id` | string | 是 | 与下行 `motion_task` 的 `task_id` 一致 |
| `phase` | string | 是 | `accepted` \| `running` \| `done` \| `failed` |
| `device_id` | string | 否 | 若下行任务体携带则回显，便于 DeviceServer 路由 |
| `capability` | string | 否 | 原始能力名字符串（与下行一致） |

示例见 `examples/motion_event.minimal.uplink.json`。

### DeviceServer → BusinessServer（HTTP）

当且仅当 `xiaozhi-server` 配置中 **`server.motion_event_business_base_url`** 与 **`server.internal_motion_task_token`** 同时非空：

- **方法 / 路径**：`POST {motion_event_business_base_url}/internal/v1/motion_event`
- **请求头**：`Authorization: Bearer {internal_motion_task_token}`（须与 `manager-api` 的 `v2.device-server.internal-token` 一致）
- **请求体**：与 WSS 根对象相同字段，但 **去掉** `type`、`session_id`（由 handler 剥离）

### BusinessServer 响应

- **200**：`Result` 包装，`code=0`；M2.6 仅打日志，不落库（**M2.9**）。

## 与 §15.2 全量上行事件的关系

架构 §15.2 中的 `event_type` / `seq` / `payload` 等为 Edge-A 与持久化准备；M2.6 用 **`phase`** 表达最小任务状态机。后续里程碑将映射或扩展为全量事件模型，并先改本目录契约再改代码。
