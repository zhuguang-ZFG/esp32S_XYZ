# M6 Privacy Permissions Runbook

Date: 2026-05-16

## Scope

This runbook covers release checks for the M6.1 privacy and permission flow.

Local mini-program surfaces:

- `/pages/settings/privacy-permissions`
- `/pages/login/privacy-policy-zh`
- `/pages/device-config/index`

Local mini-program manifest declarations:

- `mp-weixin.permission.scope.record` describes microphone usage for voice commands and voiceprint enrollment.
- `mp-weixin.permission.scope.userLocation` and `requiredPrivateInfos: getLocation` remain tied to Wi-Fi provisioning compatibility.
- `manager-mobile/vite.config.ts` patches the generated `dist/build/mp-weixin/app.json` so `scope.record` is present in the WeChat build artifact.

Permission purposes:

- microphone: voice input and voiceprint-related user action
- Bluetooth: device provisioning and nearby device configuration
- Wi-Fi: SoftAP provisioning fallback and local network setup

## Local Checks

Run from the repository root:

```powershell
rtk python -m unittest tests.ci.test_manager_mobile_privacy_permissions -v
```

Run from `server/xiaozhi-esp32-server/main/manager-mobile`:

```powershell
rtk corepack pnpm type-check
rtk corepack pnpm run build:mp-weixin
rtk node -e "const fs = require('node:fs'); const app = JSON.parse(fs.readFileSync('dist/build/mp-weixin/app.json', 'utf8')); const permission = app.permission || {}; if (!permission['scope.record']) throw new Error('missing scope.record'); if (!permission['scope.userLocation']) throw new Error('missing scope.userLocation'); if (!(app.requiredPrivateInfos || []).includes('getLocation')) throw new Error('missing getLocation private info');"
```

## WeChat Mini-Program Console Checklist

Before release, attach evidence from the WeChat mini-program console:

- privacy policy URL is configured and matches the in-app privacy policy entry
- microphone API purpose is declared
- Bluetooth API purpose is declared
- Wi-Fi API purpose is declared
- subscription/notification wording, if enabled later, matches the privacy policy
- reviewers can reach the privacy and permission page from Settings

Do not mark this gate complete from repository evidence alone.

## Device Acceptance Drill

Use a real WeChat mini-program build and a physical test phone.

1. Open Settings and navigate to the privacy/permission page.
2. Open the privacy policy from that page.
3. Trigger microphone permission and capture the OS/WeChat prompt.
4. Deny microphone permission and confirm the fallback text and settings entry are visible.
5. Trigger Bluetooth permission from provisioning.
6. Deny or disable Bluetooth and confirm the fallback text and settings entry are visible.
7. Trigger Wi-Fi setup or SoftAP fallback.
8. Deny or disable Wi-Fi and confirm the fallback text and settings entry are visible.
9. Re-enable permissions through system settings and confirm the flow can continue.

Attach evidence:

- build version
- phone model and OS version
- WeChat version
- privacy policy screenshot
- microphone prompt and fallback screenshots
- Bluetooth prompt and fallback screenshots
- Wi-Fi prompt and fallback screenshots
- reviewer notes or WeChat review result

## Privacy Permissions Evidence Gap Record

Use this structure when WeChat console, real permission prompt, or review evidence is missing for a release candidate. It is not evidence of external privacy approval.

- missing evidence scope: privacy policy URL, microphone purpose, Bluetooth purpose, Wi-Fi purpose, generated permission artifact, real permission prompt, fallback screenshot, reviewer notes, or WeChat review approval
- environment: staging build, release-candidate build, physical phone, simulator, or WeChat console
- owner: named product or compliance owner accountable for closing the gap
- due date: calendar date for closing the missing evidence
- fallback path: keep the in-app privacy/permission page reachable, keep per-permission fallback prompts, block release if required WeChat declarations or real prompt screenshots are missing
- risk acceptance: product owner, compliance owner, and release manager approval reference
- rollback trigger: missing WeChat declaration, generated artifact lacks required permission fields, real prompt text mismatches policy wording, fallback prompt is not reachable, or review rejects the privacy declaration
- follow-up evidence: console privacy declaration screenshot, generated `app.json`, build version, phone model, OS version, WeChat version, prompt screenshots, fallback screenshots, privacy policy screenshot, reviewer notes, and review approval result

## Current Local Limitation

The repository implements the mini-program-side pages and fallback flows, but it does not prove WeChat console privacy declarations, real permission prompts, or review approval.
