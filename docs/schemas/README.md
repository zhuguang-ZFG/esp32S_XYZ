# Schemas

This directory holds protocol contracts, JSON Schema files, and examples for all four edges of the motion-control stack.

## Layout

| Edge | Path | Role | Contents |
|------|------|------|----------|
| Edge-A | `edge_a/` | Client ↔ BusinessServer WSS | `auth`, `subscribe_device`, `subscribe_task`, `unsubscribe`, `ack`, `ping` (client); `authed`, `subscribed`, `unsubscribed`, `acked`, `event(job_status)`, `event(device_info_reply)`, `pong`, `error` (server) — 14 schemas |
| Edge-B | `edge_b/` | BusinessServer ↔ DeviceServer HTTP | `motion_task`, `motion_event`, `intent_submit` — 3 schemas |
| Edge-C | `edge_c/` | DeviceServer ↔ U8 WSS | `motion_task` (downlink), `motion_event` / `device_info` (uplink) — 3 schemas |
| Edge-D | `edge_d/` | U8 ↔ U1 UART JSON | `cmd`, `ack`, `device_info`, `status`, `result`, `error` — 6 schemas, 11 examples |

See each edge's `README.md` for protocol details and field semantics.

## Completion

26 schemas + 36 examples across all 4 edges. JSON Schema 2020-12, `additionalProperties: false`, `const` discriminators.

## Field evolution rule

Any future field change should update these contract artifacts first, then update
the implementation and tests that consume them.
