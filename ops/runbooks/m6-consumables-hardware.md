# M6 Consumables Hardware Acceptance Runbook

Date: 2026-05-16

## Scope

This runbook covers hardware-facing acceptance checks for M6.6 consumables:

- `paper_slot_state`
- `pen_installed_at`
- `pen_ink_percent_est`
- `pen_mileage_mm`
- `E_NO_PAPER` write/draw gate

The current implementation is manual/estimated. It does not use a physical paper, ink, or pressure sensor.

## Local Checks

Run from the repository root:

```powershell
rtk python -m unittest tests.ci.test_manager_api_consumables tests.ci.test_manager_mobile_device_info -v
```

Run from `server/xiaozhi-esp32-server/main/manager-api`:

```powershell
rtk mvn "-Dtest=DeviceSupplyServiceImplTest,AppV2ServiceImplTest,AppV2ControllerTest" test
```

Run from `server/xiaozhi-esp32-server/main/manager-mobile`:

```powershell
rtk corepack pnpm type-check
```

## Device Acceptance Drill

Use a physical staging device.

1. Open the device detail page.
2. Mark paper as `empty`.
3. Submit `write_text` and confirm the API returns `E_NO_PAPER`.
4. Submit `draw_generated` and confirm the API returns `E_NO_PAPER`.
5. Mark paper as `loaded`.
6. Submit `write_text` and confirm the task is accepted if other safety checks pass.
7. Mark a new pen installed and confirm `pen_ink_percent_est = 100`.
8. Attempt to set `pen_ink_percent_est = 101` and confirm the API rejects it without insert/update.
9. Attempt to set `pen_ink_percent_est = -1` and confirm the API rejects it without insert/update.
10. Submit a known square `run_path`.
11. Record the exact `run_path` payload and expected mileage formula: sum each line segment as `sqrt(dx^2 + dy^2 + dz^2)` in millimeters.
12. Wait for a `done` motion event.
13. Confirm `pen_mileage_mm` increases by the expected path length within <= 1 mm tolerance, or attach the firmware/controller rounding rule that explains a wider tolerance.
14. Set paper state to `unknown`.
15. Confirm `unknown` paper state does not block write/draw submission.

Attach evidence:

- device id and firmware version
- manager-mobile screenshots for empty, loaded, unknown, and new-pen states
- API request/response logs for invalid `pen_ink_percent_est` values
- API request/response logs for `E_NO_PAPER`
- exact `run_path` payload, expected mileage calculation, and accepted tolerance
- motion event showing `run_path` reached `done`
- before/after `pen_mileage_mm`

## Sensor Integration Gate

Before claiming hardware sensing support, attach evidence for:

- paper sensor electrical signal and firmware mapping
- ink or pressure sensing method
- calibration range and failure mode
- UI state when sensor data is stale or unavailable
- tests showing sensor-backed state can override or inform manual state

Until then, keep the product language as manual/estimated.

## Consumables Evidence Gap Record

Use this structure when consumables hardware or mileage evidence is missing for a release candidate. It is not evidence of physical sensing or real-device mileage accuracy.

- missing evidence scope: paper state screenshots, `E_NO_PAPER` request logs, invalid ink-percent request logs, exact `run_path` payload, expected mileage calculation, before/after `pen_mileage_mm`, paper sensing, ink sensing, pressure sensing, or sensor stale-state UI
- environment: staging, production, or named release-candidate device
- owner: named product or hardware owner accountable for closing the gap
- due date: calendar date for closing the missing evidence
- fallback path: keep consumables labeled manual/estimated, keep `unknown` paper state allowed, require user-marked `empty` for blocking, and do not claim sensor-backed paper or ink status
- risk acceptance: product owner, hardware owner, and release manager approval reference
- rollback trigger: physical paper state disagrees with UI, `E_NO_PAPER` gate fails, invalid ink percent persists, mileage delta exceeds accepted tolerance without a documented rounding rule, or sensor-backed state is claimed without evidence
- follow-up evidence: device id, firmware version, mobile screenshots, redacted API logs, exact `run_path` payload, expected mileage calculation, motion `done` event, before/after mileage values, tolerance note, sensor calibration proof, and stale-state UI screenshot

## Current Local Limitation

The repository proves the backend contract, mobile controls, and static/unit behavior. It does not prove physical paper sensing, ink sensing, pressure sensing, or real-device mileage accuracy.
