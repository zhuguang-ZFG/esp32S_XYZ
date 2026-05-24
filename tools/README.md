# Tools

This directory contains design-time verification tools for the M0 phase.

## GPIO Static Checker

`check_gpio.py` validates the U1 and U8 GPIO configuration before hardware bring-up.

It checks:

1. duplicate output GPIO assignments within the same MCU;
2. ESP32-S3 strapping pins used as normal outputs without an explicit known-risk marker;
3. U8-to-U1 UART TX/RX crossover configuration;
4. ESP32-S3-WROOM-1-N16R8 unavailable pins;
5. weak-evidence pins, if any remain after PADS source-file verification.

Run:

```bash
rtk python tools/check_gpio.py
rtk python tools/test_check_gpio.py -v
rtk python -m unittest tools.tests.test_check_gpio -v
```

Expected current result:

```text
OK: GPIO check passed; no issues found
```

`GPIO38`, `GPIO39`, and `GPIO40` are no longer weak evidence: `docs/DLC_Motor_Control_P1_V1.0_260513.txt` maps them to U8.31/32/33 through PADS `*SIGNAL*` records. M0f still needs real-board continuity and timing checks.

## JSON Schema Validator

`validate_schemas.py` validates every JSON Schema under `docs/schemas/` and checks that every example in each edge directory matches exactly one schema in that same edge.

Run:

```bash
rtk python tools/validate_schemas.py
```

Expected current result:

```text
validated=62 passed=62 failed=0
```

## M0c Fake Tools

These fakes let M1-M4 software work run without real U1/U8 hardware or real AI providers.

Run focused tests:

```bash
rtk python -m unittest tools.fake_u1.tests.test_app -v
rtk python -m unittest tools.fake_device_server.tests.test_app -v
rtk python -m unittest tools.fake_ai.tests.test_app -v
```

Useful CLI entry points:

```bash
rtk python tools/fake_u1/app.py --help
rtk python tools/fake_device_server/app.py --help
rtk python tools/fake_ai/app.py --help
rtk python tools/fake_lima_u8/app.py --help
```

Notes:

- fake U1 `status`, `result`, and `error` responses are covered by Edge-D schema assertions.
- Edge-D has dedicated `ack` and `device_info` schemas now; fake U1 validates both response shapes.
- fake DeviceServer maps `get_device_info` motion tasks to fake U1 `GET_DEVICE_INFO` and returns a `device_info` report payload.
- fake DeviceServer expands `run_path` motion tasks into `PATH_BEGIN` / `PATH_SEG` / `PATH_END` Edge-D command sequences.
- fake AI emits Edge-B shaped `intent_submit` payloads for deterministic device-intent tests.
- fake LiMa U8 speaks LiMa `/device/v1/ws` in text mode: `hello`, `heartbeat`,
  `transcript`, `motion_event`, and expected `motion_task` handling. Its unit
  tests use an in-memory transport, while the CLI imports `websockets` only
  when connecting to a real LiMa server.

## CI Coverage

`.github/workflows/ci.yml` runs:

- schema validation;
- GPIO static check;
- Python unit tests;
- fake U1 integration tests;
- manager-api App V2 / Edge-A WSS Maven tests;
- Markdown link check.

## References

- `docs/实施计划-v2.md`
- `docs/全局规划-Planning-with-Files.md`
- `docs/硬件连接与GPIO分配说明.md`
