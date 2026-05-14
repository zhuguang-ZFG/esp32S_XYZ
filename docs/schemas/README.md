# Schemas

This directory holds protocol contracts, JSON Schema files, and examples for all four edges of the motion-control stack.

## Layout

| Edge | Path | Role | Contents |
|------|------|------|----------|
| Edge-A | `edge_a/` | Client ↔ BusinessServer WSS | `auth`, `subscribe_device`, `subscribe_task`, `unsubscribe`, `ack`, `ping` (client); `authed`, `subscribed`, `event(job_status)`, `pong`, `error` (server) — 10 schemas |
| Edge-B | `edge_b/` | BusinessServer ↔ DeviceServer HTTP | `motion_task`, `motion_event`, `intent_submit` — 3 schemas |
| Edge-C | `edge_c/` | DeviceServer ↔ U8 WSS | `motion_task` (downlink), `motion_event` (uplink) — 2 schemas |
| Edge-D | `edge_d/` | U8 ↔ U1 UART JSON | `cmd`, `status`, `result`, `error` — 4 schemas, 7 examples |

See each edge's `README.md` for protocol details and field semantics.

## Completion

19 schemas + 22 examples across all 4 edges. JSON Schema 2020-12, `additionalProperties: false`, `const` discriminators.

## Field evolution rule

Any future field change should update these contract artifacts first, then update
the implementation and tests that consume them.
