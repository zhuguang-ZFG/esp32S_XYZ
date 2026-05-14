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
python tools/check_gpio.py
python tools/test_check_gpio.py -v
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
python tools/validate_schemas.py
```

Expected current result:

```text
validated=41 passed=41 failed=0
```

## CI Coverage

`.github/workflows/ci.yml` runs:

- schema validation;
- GPIO static check;
- Python unit tests;
- fake U1 integration tests;
- Markdown link check.

## References

- `docs/实施计划-v2.md`
- `docs/全局规划-Planning-with-Files.md`
- `docs/硬件连接与GPIO分配说明.md`
