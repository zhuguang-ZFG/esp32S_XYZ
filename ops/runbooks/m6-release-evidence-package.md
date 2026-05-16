# M6 Release Evidence Package Runbook

Date: 2026-05-16

## Scope

This runbook defines the evidence package required before an M6 production launch. It complements:

- `docs/M5-local-evidence-manifest.md`
- `docs/M6-local-evidence-manifest.md`
- `docs/M6.3-release-compliance-checklist.md`
- `docs/M6-closeout-audit.md`

It does not claim ICP filing, MLPS assessment, WeChat review, legal signoff, hardware certification, or release approval is complete.

## Evidence Folder Structure

Create one release evidence folder per candidate build:

```text
M6-release-evidence/
  00-local-manifests/
  01-icp-domain/
  02-mlps-readiness/
  03-pipl-privacy/
  04-ai-content-provider/
  05-hardware-ota-certification/
  06-operations-runbooks/
  07-signoff/
```

## Required Evidence

Local implementation manifests:

- M5 local evidence manifest plus real evidence closing its open hardware, provisioning, OTA, and production-key gates
- M6 local evidence manifest plus reviewed evidence for every remaining M6 open gate
- current M6 closeout audit, including the latest local verification outputs

ICP and domain:

- business entity proof
- ICP filing number or filing status screenshot
- DeviceServer and BusinessServer domain screenshots
- WeChat mini-program backend domain configuration screenshot
- CDN, object storage, and OTA download domain list

MLPS 2.0 readiness:

- system boundary diagram
- asset inventory
- administrator account list
- permission matrix
- log-retention policy
- backup and restore drill record
- high-risk gap owner list

PIPL and privacy:

- generated WeChat mini-program `dist/build/mp-weixin/app.json` permission artifact showing `scope.record`, `scope.userLocation`, and `requiredPrivateInfos: getLocation`
- privacy policy version
- WeChat console privacy declaration screenshot
- microphone, Bluetooth, and Wi-Fi purpose screenshots
- real permission prompt screenshots from a device or simulator build
- WeChat privacy review approval evidence
- voiceprint separate consent design
- child/guardian consent path
- account deletion and voiceprint deletion drill evidence
- retention cleanup runbook and post-run evidence when deployed

AI content provider:

- provider compliance material
- content audit policy
- child-scenario content policy
- blocked-content audit evidence
- statement that generated content is not used for ad targeting unless separately approved

Hardware and OTA:

- SRRC / CCC / RoHS / GB 6675 path decision
- OTA metadata integrity evidence
- firmware sha256 and signing evidence
- rollout stop threshold
- health-check and diagnostics evidence
- consumables hardware acceptance evidence
- paper sensing, ink/pressure sensing, and real-device mileage accuracy evidence with recorded `run_path` payload, expected mileage calculation, and accepted tolerance, or explicit manual/estimate limitation signoff
- RMA factory credential cleaning evidence

Operations runbooks:

- `ops/runbooks/m6-privacy-permissions.md`
- `ops/runbooks/m6-retention-cleanup.md`
- `ops/runbooks/m6-product-notifications.md`
- `ops/runbooks/m6-consumables-hardware.md`
- `ops/runbooks/m6-device-rma.md`
- `ops/runbooks/m6-monitoring-alerts.md`

Production-operation evidence:

- platform notification template approval, opt-in, send-attempt log, delivery screenshot, and deep-link screenshot for pending device transfer
- platform notification template approval, opt-in, send-attempt log, delivery screenshot, and deep-link screenshot for pending primary voice approval
- native tabBar badge before/after screenshots on the target WeChat client versions
- background reminder delivery evidence or explicit release deferral with owner, due date, fallback path, and risk acceptance
- production retention scheduler configuration and post-run deletion logs for privacy, content-audit, and safety-audit cleanup jobs
- production RMA operator permission assignment, gateway policy evidence, exported RMA audit review, factory credential cleaning proof, and deployed DeviceServer refusal drill
- deployed internal runtime endpoint smoke for `/internal/v1/motion_event`, `/internal/v1/device_info`, `/internal/v1/self_check`, `/internal/v1/voice_task`, `/internal/v1/voiceprints/cache`, `/internal/v1/firmware/upgrade-plan`, and `/internal/v1/firmware/install-result`, showing missing or wrong Bearer token refusal and valid internal Bearer token acceptance at the controller boundary
- Prometheus scrape health, redacted `authorization.credentials_file` config, redacted `MANAGER_API_SECRET_FILE` or secret mount evidence, 401 refusal evidence for a missing or wrong Manager API scrape Bearer token, Grafana dashboard screenshot, Alertmanager route screenshot, and DingTalk/WeCom webhook delivery evidence

Signoff:

- product owner signoff
- legal signoff
- technical owner signoff
- operations owner signoff
- release manager approval

## Local Verification

Run from the repository root:

```powershell
rtk python -m unittest tests.ci.test_docs_m6_compliance_checklist -v
```

The local test only verifies checklist/runbook presence and guardrails. It does not verify the contents of external screenshots, filings, assessments, or approvals.

## Release Gate Rule

Do not mark M6 complete or production-ready unless every folder above contains reviewed evidence and each signoff owner approves the release.

If evidence is missing:

- keep the item open in the release ticket
- record the owner, due date, fallback path, risk acceptance, rollback trigger, and follow-up evidence
- do not replace the evidence with repository tests or local runbooks

## Current Local Limitation

This runbook is a packaging contract. It does not prove external approval, WeChat console privacy declarations, real permission prompt behavior, hardware certification, production monitoring delivery, factory cleaning execution, production OTA readiness, or WeChat review completion. The M5 and M6 local evidence manifests help organize prerequisites and open gates, but they do not replace real hardware, deployment, compliance, or production-operation evidence.
