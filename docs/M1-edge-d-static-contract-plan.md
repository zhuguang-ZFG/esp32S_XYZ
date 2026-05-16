# M1 Edge-D firmware static contract plan

Date: 2026-05-15

## Scope

This pass fixes firmware-side Edge-D contract drift that can be checked without hardware.

## Findings

- U8 `BuildProtocolCommandJson()` emitted `type:"cmd"` in command frames, but `docs/schemas/edge_d/cmd.schema.json` has `additionalProperties:false` and does not allow that field.
- U1 private protocol errors used `E010` / `E003` in several paths, while Edge-D `error.schema.json` currently only permits the documented error-code enum.
- U1 `ack` responses did not consistently include `accepted:true`, while fake U1 and higher-level tests already treat that field as the deterministic accepted marker.
- `GET_DEVICE_INFO` existed in the command enum and fake path, but U1 firmware did not handle it, U8 did not expose it as a capability, and Edge-D had no dedicated response schema.

## Changes

- `firmware/u8-xiaozhi/main/boards/zhuguang/dlc-motor-control-p1-ai/dlc_motor_control_p1_ai_board.cc`
  - Removed `type:"cmd"` from generated Edge-D command frames.
  - Added `GET_DEVICE_INFO` command sending, response field passthrough, motion_task handling, and MCP tool exposure.
- `firmware/u1-grbl/Grbl_Esp32/src/Protocol.cpp`
  - Normalized unsupported/invalid private protocol errors to `E009`.
  - Added `accepted:true` to `send_private_protocol_ack_with_state()`.
  - Added `accepted:true` to direct `PATH_BEGIN` and `PATH_SEG` ack responses.
  - Added minimal `GET_DEVICE_INFO` response with `model / hw_rev / fw_rev / workspace_mm`.
- `docs/schemas/edge_d/`
  - Added `ack.schema.json` and `device_info.schema.json` with matching examples.
- `tests/ci/test_edge_d_firmware_static.py`
  - Added static checks for U8 command shape, U8/U1 `GET_DEVICE_INFO`, U1 error-code drift, and U1 ack `accepted:true` coverage.

## Local Verification

```bash
rtk python -m unittest tests.ci.test_edge_d_firmware_static -v
rtk python -m unittest discover -s tests -p "test_*.py" -v
rtk python tools/validate_schemas.py
rtk python tools/check_gpio.py
rtk powershell.exe -NoProfile -Command "& 'C:\Users\zhugu\.platformio\penv\Scripts\platformio.exe' run -e release_esp32s3"
rtk powershell.exe -NoProfile -Command "git diff --check"
```

Current result: all tests/checks pass locally, and U1 `release_esp32s3` builds successfully from `firmware/u1-grbl`; `rtk git diff --check` only reports accepted LF/CRLF conversion warnings.

## Remaining Limits

- No hardware was used in this pass.
- U8 ESP-IDF build was not run in this environment because `idf.py` is not available; U8 coverage is static-only for now.
- Remote GitHub Actions have not been triggered in this session.
- Edge-D now has dedicated `ack.schema.json` and `device_info.schema.json`; real hardware identity fields still require M0f/firmware verification.
