# Tools

This directory contains design-time verification tools for the M0 phase.

## GPIO Static Checker

`check_gpio.py` validates the U1 and U8 GPIO configuration before hardware bring-up.

It checks:

1. duplicate output GPIO assignments within the same MCU;
2. ESP32-S3 strapping pins used as normal outputs without an explicit known-risk marker;
3. U8-to-U1 UART TX/RX crossover configuration;
4. ESP32-S3-WROOM-1-N16R8 unavailable pins;
5. weak-evidence pins that must be verified on real hardware.

Run:

```bash
python tools/check_gpio.py
python tools/test_check_gpio.py -v
```

Expected current result:

```text
Total: 0 errors, 0 warnings, 3 info
```

The three info items are weak-evidence U8 pins `GPIO38`, `GPIO39`, and `GPIO40`; they are allowed for software progress but must be verified during M0f real-board bring-up.

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
