# M0c fake tools plan

Date: 2026-05-15

## Scope

M0c provides software-only fakes so M1-M4 work can proceed before real hardware:

- `tools/fake_u1/app.py`: U8-to-U1 Edge-D private protocol fake.
- `tools/fake_device_server/app.py`: minimal HTTP fake DeviceServer that forwards motion tasks to fake U1.
- `tools/fake_ai/app.py`: deterministic fake ASR / intent / TTS provider.

## Contract decisions

- fake U1 uses `@{json}\n` transport and accepts the current Edge-D `cmd.schema.json` command set.
- `status`, `result`, and `error` responses are tested against Edge-D schemas.
- Edge-D now has a dedicated `ack.schema.json`; fake U1 ack responses are schema-locked alongside `status/result/error`.
- Edge-D now has a dedicated `device_info.schema.json`; `GET_DEVICE_INFO` returns a schema-locked result frame with `model / hw_rev / fw_rev / workspace_mm`.
- Hardware open risks from M0g remain open and are not represented as closed-loop fake capabilities.

## Changes

- Added `E006` injection and kept injected codes limited to `E001/E005/E006/E008`.
- Mapped unsupported/invalid fake U1 failures to schema-valid `E009` instead of non-contract codes.
- Added `GET_DEVICE_INFO`, `PAUSE`, `RESUME`, and `STOP` handling in the lightweight fake U1.
- Added soft-limit checks for MOVE / PATH segment coordinates.
- Added `--latency-ms` to fake U1.
- Refactored fake DeviceServer motion-task-to-U1 command construction into a testable function.
- Added fake AI provider with deterministic ASR text, Edge-B shaped intent payloads, and silent WAV TTS output.

## Verification

Passing checks during this round:

```bash
rtk python -m unittest tools.fake_u1.tests.test_app -v
rtk python -m unittest tools.fake_device_server.tests.test_app -v
rtk python -m unittest tools.fake_ai.tests.test_app -v
rtk python tools/fake_u1/app.py --help
rtk python tools/fake_device_server/app.py --help
rtk python tools/fake_ai/app.py --help
```

Full regression commands are tracked in `tools/README.md`.
