# M6 Device RMA Runbook

Date: 2026-05-16

## Scope

This runbook covers operational checks for M6 device repair, return, restock, and disposal.

Backend API endpoints:

- `POST /api/v1/devices/{deviceId}/rma/start`
- `POST /api/v1/devices/{deviceId}/rma/complete`
- `POST /api/v1/devices/{deviceId}/return`
- `POST /api/v1/devices/{deviceId}/restock`
- `POST /api/v1/devices/{deviceId}/dispose`
- `POST /api/v1/devices/{deviceId}/rma/events`

Runtime refusal endpoint used by xiaozhi-server WebSocket auth:

- `POST /config/device-runtime-status`

## Operator Access

The current M6 backend gates RMA endpoints with either:

- the existing manager-api `sys:role:superAdmin` permission
- the narrower manager-api `appv2:device:rma` permission

Before production use, operations must still prove the deployed operator/admin access path, gateway policy, and audit trail around this minimal slice.

Required production controls:

- operator identity is tied to a named person or service account
- RMA action is approved in a ticket before execution
- request id, operator id, device id, device sn, action, and timestamp are recorded
- non-operator accounts cannot call RMA endpoints in production routing or gateway policy
- RMA-only operators receive `appv2:device:rma` without broader super-admin permissions unless explicitly approved

This repository proves only the local Shiro permission contract. It does not prove those production controls are deployed.

## Local Audit Trail

Each backend RMA transition writes a `device_rma_events` row containing:

- operator account id
- device id and device serial number
- RMA action
- previous and new device status
- previous and new binding status
- factory cleaning attestation flag and external evidence reference when supplied
- external RMA ticket reference when `ticketRef` is supplied
- timestamp

The audit row intentionally does not store activation codes or free-form RMA notes. Use `ticketRef` for local correlation with the approved RMA ticket id. Production evidence must still correlate these local audit rows with the gateway request id and named operator identity.

For evidence export, call `POST /api/v1/devices/{deviceId}/rma/events` as an RMA operator or super-admin and attach the redacted response to the RMA ticket. Confirm the response includes device id, device sn, operator account id, action, status transitions, `ticketRef`, optional `evidenceRef`, and timestamp, and does not include activation codes or free-form notes.

## Backend State Transitions

Repair:

- `bound -> rma_in_progress`
- active binding becomes `rma_in_progress`
- `rma_in_progress -> bound`
- binding returns to `active`

Return and restock:

- `bound -> returned`
- active binding becomes `returned`
- activation code becomes `revoked`
- `returned -> provisioned_unbound`
- activation code is refreshed and becomes `provisioned`

Dispose:

- known device state becomes `disposed`
- known binding becomes `disposed`
- activation code becomes `revoked`
- BusinessServer runtime entrypoints reject disposed devices with `E_DEVICE_DISPOSED`
- xiaozhi-server rejects disposed devices during WebSocket auth when `manager-api.url` and `manager-api.secret` are configured

## Factory Credential Cleaning

Factory credential cleaning is an offline production process and is not performed by the manager-api RMA status transition.

Before restock or disposal is accepted:

- erase device NVS credentials and Wi-Fi provisioning data
- erase user tokens, voiceprint cache, and any local personal data
- regenerate or remove activation material according to the target state
- flash approved production firmware if the device is restocked
- run the device self-check procedure and record the result
- attach photo or serial-console evidence to the RMA ticket

For returned devices moving to resale, the backend `restock` action requires `factoryCleaned = true` and a non-empty `evidenceRef`, and must only be run after factory cleaning is completed.

For disposed devices, `dispose` also requires `factoryCleaned = true` and a non-empty `evidenceRef`. Do not reuse activation material. Keep the backend device state as `disposed`.

## Pre-Deployment Checks

Run from the repository root:

```powershell
rtk python -m unittest tests.ci.test_manager_api_device_rma tests.ci.test_xiaozhi_server_disposed_auth -v
```

The xiaozhi-server disposed-auth check is a repository-level static CI test that reads files under `server/xiaozhi-esp32-server/main/xiaozhi-server`. Run it from the repository root; do not run the `tests.ci` module path from inside the xiaozhi-server subdirectory.

Run from `server/xiaozhi-esp32-server/main/manager-api`:

```powershell
rtk mvn "-Dtest=DeviceRmaServiceImplTest,AppV2ControllerTest,InternalMotionEventControllerTest,ConfigControllerTest" test
```

## Disposed Device Refusal Drill

Use a staging device id and serial number.

1. Mark the device disposed through `POST /api/v1/devices/{deviceId}/dispose`.
2. Confirm `POST /config/device-runtime-status` returns `disposed=true` for both device id and serial number lookup paths.
3. Attempt a manager-api internal runtime event for that device and confirm `E_DEVICE_DISPOSED`.
4. Attempt xiaozhi-server WebSocket auth with the disposed device and confirm `E_DEVICE_DISPOSED`.
5. Confirm local whitelist or token bypass does not admit the disposed device when manager-api is configured.

Attach evidence:

- RMA ticket id
- exported `device_rma_events` rows from `POST /api/v1/devices/{deviceId}/rma/events`
- API request/response redacted logs
- manager-api runtime refusal log
- xiaozhi-server WebSocket auth refusal log
- factory credential cleaning checklist result

## Restock Drill

Use a staging returned device.

1. Confirm the device is in `returned`.
2. Complete factory credential cleaning.
3. Run `POST /api/v1/devices/{deviceId}/restock`.
4. Confirm the device becomes `provisioned_unbound`.
5. Confirm a fresh activation code is generated or the approved replacement activation code is stored.
6. Confirm old account history is not migrated to the next owner.

## RMA Evidence Gap Record

Use this structure when production RMA evidence is missing for a release candidate. It is not evidence that physical handling, operator access, or deployed refusal checks were performed.

- missing evidence scope: operator permission assignment, gateway policy, approved RMA ticket, exported audit review, factory credential cleaning proof, disposed-device runtime refusal, or xiaozhi-server WebSocket refusal
- environment: staging, production, or named release-candidate environment
- owner: named operations owner accountable for closing the gap
- due date: calendar date for closing the missing evidence
- fallback path: keep RMA actions restricted to super-admin, hold restock/dispose, keep device out of resale, and rely on BusinessServer disposed-state refusal until deployed DeviceServer refusal evidence is attached
- risk acceptance: operations owner, technical owner, and release manager approval reference
- rollback trigger: non-operator access succeeds, audit export is missing, factory cleaning proof is missing, disposed-device runtime entrypoint accepts traffic, or xiaozhi-server admits a disposed device
- follow-up evidence: role assignment screenshot, gateway policy export, approved RMA ticket, redacted `device_rma_events` export, factory cleaning photo or serial-console log, runtime refusal logs, and WebSocket refusal logs

## Current Local Limitation

This runbook is local delivery evidence and an operations checklist. It does not prove production operator permission assignment, gateway policy, production ticket correlation review, factory credential cleaning, or physical device handling were performed.
