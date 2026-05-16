# M0a 实施计划：JSON Schema 契约固化

**创建日期**: 2026-05-15
**状态**: 已完成
**目标**: 补齐 `docs/schemas/` 四条边的 JSON Schema 2020-12 契约、样例和 README 覆盖缺口

## 0. 前置审计

- [x] 已读 `docs/实施计划-v2.md` M0a 完成判定：四条边 schema、`docs/schemas/README.md`、字段演进规则。
- [x] 已读 `docs/接续指令.md`：M0g §1 已完成，M0a 下一步。
- [x] 已盘点 `docs/schemas/edge_a/b/c/d` 现有文件。
- [x] 已核对 Edge-A 实现 `ClientEdgeWebSocketHandler.java` 的实际服务端响应。

## 1. 发现的缺口

- [x] Edge-D 缺 `README.md`，但顶层 README 写明每条边应有 README。
- [x] Edge-D `cmd.schema.json` 未覆盖 v2 §5.4 命令集里的 `GET_DEVICE_INFO`。
- [x] Edge-A 实现会发送 `type=authed`，但缺 `authed.schema.json` 与样例。
- [x] Edge-A 实现会发送 `type=acked`，但缺 `acked.schema.json` 与样例。
- [x] Edge-A 实现会发送 `type=unsubscribed`，但缺 `unsubscribed.schema.json` 与样例。
- [x] 顶层 `docs/schemas/README.md` 与 Edge-A README 的 schema/example 计数需要随新增文件更新。

## 2. 将修改的文件

- `docs/schemas/README.md`
- `docs/schemas/edge_a/README.md`
- `docs/schemas/edge_a/authed.schema.json`
- `docs/schemas/edge_a/acked.schema.json`
- `docs/schemas/edge_a/unsubscribed.schema.json`
- `docs/schemas/edge_a/examples/server.authed.json`
- `docs/schemas/edge_a/examples/server.acked.json`
- `docs/schemas/edge_a/examples/server.unsubscribed.json`
- `docs/schemas/edge_d/README.md`
- `docs/schemas/edge_d/cmd.schema.json`
- `docs/M0a-schema-plan.md`
- `docs/接续指令.md`

## 3. 验证命令

```powershell
rtk python -m json.tool docs/schemas/edge_a/authed.schema.json
rtk python -m json.tool docs/schemas/edge_d/cmd.schema.json
rtk python -c "<jsonschema validation script>"
rtk powershell.exe -NoProfile -Command "git diff --check"
rtk git status --short --branch
```

## 4. 完成判定

- [x] Edge-A 服务端实际响应 `authed/subscribed/unsubscribed/acked/event/pong/error` 都有 schema。
- [x] Edge-D v2 命令集 `HOME/MOVE/PATH_BEGIN/PATH_SEG/PATH_END/GET_STATUS/GET_DEVICE_INFO/PAUSE/RESUME/STOP/ESTOP` 被 schema 覆盖。
- [x] 每条边都有 README。
- [x] 所有 JSON 文件可解析，新增样例可由对应 schema 校验。
- [x] 顶层 README 的 schema/example 计数与文件数一致。
- [ ] commit/push 等待用户明确允许。

## 5. 修订记录

- 2026-05-15: 初始版本，记录 M0a schema 覆盖缺口
- 2026-05-15: 补齐 Edge-A `authed/acked/unsubscribed` schema 与样例；补齐 Edge-D README；`cmd.schema.json` 新增 `GET_DEVICE_INFO`
- 2026-05-15: `jsonschema` 校验通过 26 个 schema 与 31 个 example
