# Schemas

This directory holds protocol contracts and examples for the motion-control stack.

## Layout

- `edge_c/`: DeviceServer ↔ U8（小智 WSS 文本 JSON）——`motion_task` / `motion_event`；见 `edge_c/README.md`。
- `edge_d/examples/`: U8 -> U1 private protocol request and response examples.

## Current scope

The first committed contract surface is Edge-D for the M1 minimal command set:

- `GET_STATUS`
- `HOME`
- `MOVE`

Examples in `edge_d/examples/` are grounded in the current U1 implementation under
`firmware/u1-grbl/Grbl_Esp32/src/Protocol.cpp` and
`firmware/u1-grbl/Grbl_Esp32/src/Report.cpp`.

Note: the example filenames follow the milestone plan wording. At the moment,
current U1 behavior returns an immediate `type: "result"` payload for `HOME`
and `MOVE` success paths, so `home.ack.json` is intentionally named after the
plan while still reflecting the current on-wire response shape.

## Field evolution rule

Any future field change should update these contract artifacts first, then update
the implementation and tests that consume them.
