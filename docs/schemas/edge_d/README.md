# Edge-D（U8 ↔ U1）UART JSON 契约

与 `docs/架构定稿-v2.md` §5.4 / §15.4 对齐：U8 与 U1 之间沿用私有串口 JSON 帧，物理帧形态为 `@{json}\n`。本目录只定义 `{json}` 对象，不包含前缀 `@` 和行尾换行。

## U8 → U1：命令请求

统一 schema：`cmd.schema.json`。

| `cmd` | 说明 | 关键字段 |
|---|---|---|
| `GET_STATUS` | 查询 U1 当前状态 | `msg_id` |
| `GET_DEVICE_INFO` | 查询设备/固件信息 | `msg_id` |
| `HOME` | 归零 | `msg_id`, `task_id`（建议） |
| `MOVE` | 单步/点位移动 | `x`, `y`, `z`, `feed` |
| `PATH_BEGIN` | 路径开始 | `task_id`, `total_segments` |
| `PATH_SEG` | 路径段 | `task_id`, `segment_index`, `segment_cmd`, `x`, `y`, `z`, `feed` |
| `PATH_END` | 路径结束并执行 | `task_id` |
| `PAUSE` | 暂停当前任务 | `task_id`（可选） |
| `RESUME` | 恢复当前任务 | `task_id`（可选） |
| `STOP` | 受控停止 | `task_id`（可选） |
| `ESTOP` | 立即进入急停态 | `task_id`（可选） |

当前 schema 固化的是字段集合、类型和命令枚举；命令特定必填关系在 M0c fake U1 / M0b 静态检查阶段继续细化，避免在未完成 U1 实现核对前把推断写死。

## U1 → U8：响应帧

| `type` | schema | 说明 |
|---|---|---|
| `ack` | `ack.schema.json` | 命令已被 U1 接收，`accepted:true` 表示进入对应处理路径；不代表任务最终完成 |
| `status` | `status.schema.json` | `GET_STATUS` 或周期状态；`error_code` 在 status 帧恒为 `null`，错误码走独立 `error` 帧 |
| `result` | `device_info.schema.json` | `GET_DEVICE_INFO` 的专用结果帧，返回 `model / hw_rev / fw_rev / workspace_mm` |
| `result` | `result.schema.json` | 命令完成结果：`DONE` / `CANCELLED` / `FAILED` |
| `error` | `error.schema.json` | 命令失败、告警或急停等错误语义 |

错误码沿用 v2 §14 / §15.4：`E001/E002/E005/E006/E007/E008/E009`。硬件核对报告中的 open risk 不在 Edge-D schema 中伪装为已闭环能力；例如 hard limit、激光、+3V3 反灌等仍必须由上层安全策略和 M0f 实测闭环。

## 样例

- `examples/get_status.request.json`
- `examples/get_status.status.json`
- `examples/get_device_info.request.json`
- `examples/get_device_info.response.json`
- `examples/estop.ack.json`
- `examples/home.request.json`
- `examples/home.ack.json`
- `examples/move.request.json`
- `examples/move.result.json`
- `examples/path_begin.ack.json`
- `examples/limit.error.json`
