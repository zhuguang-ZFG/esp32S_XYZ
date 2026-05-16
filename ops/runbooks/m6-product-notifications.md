# M6 Product Notifications Runbook

Date: 2026-05-16

## Scope

This runbook covers the platform notification gap for:

- pending incoming device transfers from M6.5
- pending primary voice approvals from M6.4

The current local product surface is pull-based:

- manager-mobile device list shows pending incoming transfers and an in-app count badge
- manager-mobile device detail shows pending voice approvals and an in-app count badge
- manager-mobile updates a best-effort native tabBar badge on the home tab from the latest pulled pending transfer and voice-approval counts, and reapplies the stored local badge count on app `onShow`
- manager-api writes a `product_notification_events` pending outbox event for each pending device transfer and pending primary voice approval, using only safe semantic fields and deep links

Native tabBar badge support is implemented as a best-effort local badge. Platform push notifications and background reminders are not implemented in this repository.

## Notification Events

Pending incoming transfer:

- trigger: `DeviceTransferService.requestTransfer(deviceId, targetUnionid)` creates `status = pending`
- local outbox: `event_type = pending_device_transfer`
- recipient: target account
- deep link: `/pages/v2/device-list/index`
- user-visible purpose: target account reviews and accepts or ignores the transfer

Pending primary voice approval:

- trigger: `AppV2ServiceImpl.submitVoiceTask(...)` persists `status = pending_primary_approval`
- local outbox: `event_type = pending_primary_voice_approval`
- recipient: current primary account/session for the device
- deep link: `/pages/v2/device-detail/index?deviceId={deviceId}`
- user-visible purpose: primary client approves or rejects the pending voice task

## Suggested Payload Fields

Use a provider-specific template, but preserve these semantic fields:

```json
{
  "event": "pending_device_transfer",
  "device_id": "dev-...",
  "target_ref_type": "device_transfer",
  "target_ref_id": "123",
  "deep_link": "/pages/v2/device-list/index"
}
```

```json
{
  "event": "pending_primary_voice_approval",
  "device_id": "dev-...",
  "target_ref_type": "task",
  "target_ref_id": "task-...",
  "deep_link": "/pages/v2/device-detail/index?deviceId=dev-..."
}
```

Do not include child-entered prompt text, raw voice transcript, biometric material, or activation codes in push payloads.

The local outbox intentionally stores only:

- `event_type`
- `recipient_account_id`
- `device_id`
- `target_ref_type`
- `target_ref_id`
- `deep_link`
- `status`

Pending outbox rows are local pre-send evidence. They are marked `resolved` after the related transfer or voice approval is accepted and `cancelled` after the related request is cancelled or rejected. A future provider worker can mark a still-pending row `sent` through `ProductNotificationOutboxService.markProviderSent(...)` or `failed` through `ProductNotificationOutboxService.markProviderFailed(...)`.

## Future Push Worker Payload Contract

`ProductNotificationOutboxService.buildSafeProviderPayload(...)` is the local payload whitelist for a future provider worker.

It may expose only:

- `event`
- `device_id`
- `target_ref_type`
- `target_ref_id`
- `deep_link`

It must not expose `recipient_account_id`, outbox `status`, child-entered prompt text, raw voice transcript, biometric material, activation codes, or requested task `capability`.

This method does not send platform notifications. It is a code-level safety contract for future WeChat subscription-message, badge, or reminder integration.

Future provider workers should:

- read only `pending` rows
- construct provider data through `buildSafeProviderPayload(...)`
- call `markProviderSent(...)` only after the provider accepts the send request
- call `markProviderFailed(...)` after a provider error, timeout, or rejected send request
- leave resolved/cancelled rows untouched

## Local Verification

Run from the repository root:

```powershell
rtk python -m unittest tests.ci.test_manager_api_primary_session tests.ci.test_manager_api_device_transfer tests.ci.test_manager_mobile_device_info -v
```

Run from `server/xiaozhi-esp32-server/main/manager-api`:

```powershell
rtk mvn "-Dtest=AppV2ServiceImplTest,DeviceTransferServiceImplTest,ProductNotificationOutboxServiceImplTest" test
```

Run from `server/xiaozhi-esp32-server/main/manager-mobile`:

```powershell
rtk corepack pnpm type-check
```

These checks cover local outbox creation and lifecycle closure, safe provider payload construction, pull-based pending lists, in-app badge counts, best-effort native tabBar badge code, and local type safety. They do not prove WeChat subscription-message approval, platform push delivery, native tabBar rendering on real clients, or background reminders.

## Permission And Privacy Checks

Before enabling platform notifications:

- confirm WeChat mini-program subscription message template approval
- confirm the privacy policy describes operational reminders
- request user opt-in before sending subscription messages
- log opt-in and send attempts without storing sensitive prompt text
- provide an in-app fallback by keeping the current pull-based lists and count badges
- if platform push or background reminders are deferred for a release candidate, record the deferral owner, due date, fallback path, and risk acceptance in the release ticket

## Release Deferral Record

Use this structure only when platform push or background reminders are intentionally deferred for a release candidate. It is not evidence of delivery.

- deferral scope: platform push delivery, background reminders, or both
- affected workflow: pending incoming device transfers or pending primary voice approvals
- owner: named accountable release owner
- due date: calendar date for closing the deferred evidence
- fallback path: pull-based pending lists, in-app count badges, and best-effort native tabBar badge refresh
- user impact: who may miss an out-of-app reminder and how they recover in-app
- risk acceptance: product owner and release manager approval reference
- rollback trigger: condition that blocks release or disables the deferred workflow
- follow-up evidence: template approval, opt-in, send-attempt log, delivery screenshot, deep-link screenshot, and background reminder evidence

## Acceptance Drill

Use staging accounts and devices.

Device transfer drill:

1. Target account opts in to the approved notification template.
2. Source account requests transfer to the target account.
3. Confirm the target account sees the in-app pending transfer badge.
4. Confirm the native home tabBar badge increments after the device list refreshes.
5. Confirm a platform notification arrives on the target account device.
6. Tap the notification and confirm it opens `/pages/v2/device-list/index`.
7. Accept the transfer and confirm the pending badge clears after refresh.

Primary voice approval drill:

1. Primary client claims the device primary session.
2. Voice path submits a write task while the primary lease is active.
3. Confirm the primary client sees the in-app pending voice approval badge.
4. Confirm the native home tabBar badge increments after the device detail page refreshes.
5. Confirm a platform notification arrives on the primary client device.
6. Tap the notification and confirm it opens the target device detail page.
7. Approve or reject the task and confirm the pending badge clears after refresh.

Attach evidence:

- template approval id
- opt-in screenshot
- redacted send attempt log
- notification delivery screenshot
- deep-link screenshot
- in-app badge and native tabBar badge before/after screenshots
- background reminder delivery evidence, or an explicit deferral record with owner, due date, fallback path, and risk acceptance

## Current Local Limitation

This runbook, the local native tabBar badge helper, and the local outbox are implementation contracts and pre-send evidence only. This evidence does not prove WeChat template approval, platform push delivery, native tabBar rendering on every platform, or background reminder delivery without real client screenshots.
