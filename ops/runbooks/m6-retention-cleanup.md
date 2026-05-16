# M6 Retention Cleanup Runbook

Date: 2026-05-16

## Scope

This runbook covers the local and deployment-facing checks for M6 retention cleanup jobs:

- Privacy deletion retention cleanup:
  - `PrivacyRetentionCleanupTask`
  - cron property `appv2.privacy.retention-cleanup-cron`
  - default `0 50 3 * * *`
- Content audit retention cleanup:
  - `ContentAuditRetentionTask`
  - cron property `appv2.content.audit.retention-cron`
  - default `0 35 3 * * *`
- Safety audit retention cleanup:
  - `SafetyAuditRetentionTask`
  - cron property `appv2.safety.audit.retention-cron`
  - default `0 20 3 * * *`

The retention window is 180 days for the current M6 implementation.

## What Each Job Deletes Or Clears

Privacy retention cleanup:

- Physically deletes expired `voiceprints` rows where `status = deleted` and `audit_retain_until <= now`.
- Clears expired deleted account tombstone fields while preserving account ids for historical joins:
  - `display_name`
  - `deleted_at`
  - `audit_retain_until`

Content audit retention cleanup:

- Deletes `content_audit` rows older than the service retention cutoff.
- Stores only hashed raw content, path, rule hit, account id, device id, and timestamp before retention cleanup.

Safety audit retention cleanup:

- Deletes `safety_audit` rows older than the service retention cutoff.
- Stores business or U1 reject reason, account id, device id, capability, and timestamp before retention cleanup.

## Pre-Deployment Checks

Run from the repository root:

```powershell
rtk python -m unittest tests.ci.test_manager_api_privacy_deletion tests.ci.test_manager_api_content_audit tests.ci.test_manager_api_resource_domain -v
```

Run from `server/xiaozhi-esp32-server/main/manager-api`:

```powershell
rtk mvn "-Dtest=PrivacyDeletionServiceImplTest,ContentAuditLogServiceTest,SafetyAuditServiceTest" test
```

Confirm scheduling is enabled and all cron expressions are present:

```powershell
rtk powershell.exe -NoProfile -Command "Get-ChildItem -Path 'src/main/java' -Recurse -Filter '*.java' | Select-String -Pattern '@EnableScheduling|retention-cleanup-cron|audit.retention-cron' -List"
```

## Deployment Configuration

Keep the three cleanup windows separated so logs and database load are easy to attribute:

```properties
appv2.safety.audit.retention-cron=0 20 3 * * *
appv2.content.audit.retention-cron=0 35 3 * * *
appv2.privacy.retention-cleanup-cron=0 50 3 * * *
```

Deployment must provide evidence that these properties are loaded by the running manager-api process. Acceptable evidence includes:

- rendered application configuration from the deployment system
- startup logs showing the active profile and loaded property source
- a change record that includes the three property values

## Dry-Run Evidence Queries

Before enabling a production cleanup window, capture row counts with read-only SQL. Replace `CURRENT_TIMESTAMP` with the deployment database equivalent if needed.

```sql
SELECT COUNT(*) AS expired_deleted_voiceprints
FROM voiceprints
WHERE status = 'deleted'
  AND audit_retain_until IS NOT NULL
  AND audit_retain_until <= CURRENT_TIMESTAMP;

SELECT COUNT(*) AS expired_deleted_account_tombstones
FROM accounts
WHERE status = 'deleted'
  AND audit_retain_until IS NOT NULL
  AND audit_retain_until <= CURRENT_TIMESTAMP;

SELECT COUNT(*) AS expired_content_audit_rows
FROM content_audit
WHERE ts < CURRENT_TIMESTAMP - INTERVAL 180 DAY;

SELECT COUNT(*) AS expired_safety_audit_rows
FROM safety_audit
WHERE ts < CURRENT_TIMESTAMP - INTERVAL 180 DAY;
```

Store query output with the deployment ticket. These counts are the expected upper bound for the next cleanup pass, not a required exact match if rows change during the window.

## Post-Run Checks

After the scheduled window, collect application logs for these messages, including zero-row runs:

- `purged expired privacy retention rows count=`
- `purged expired content_audit rows count=`
- `purged expired safety_audit rows count=`
- absence of:
  - `privacy retention cleanup task failed`
  - `content_audit retention task failed`
  - `safety_audit retention task failed`

Then rerun the dry-run queries and compare the new counts with the pre-run counts.

## Retention Evidence Gap Record

Use this structure when production scheduler or post-run cleanup evidence is missing for a release candidate. It is not evidence that retention cleanup has run.

- missing evidence scope: loaded cron configuration, scheduler enabled, pre-run counts, post-run logs, failure-log absence, post-run counts, backup coverage, or PITR readiness
- environment: staging, production, or named release-candidate environment
- owner: named operations owner accountable for closing the gap
- due date: calendar date for closing the missing evidence
- fallback path: keep cleanup disabled, keep retention rows intact, run read-only dry-run queries, and hold release if expired sensitive rows cannot be explained
- risk acceptance: privacy owner, technical owner, and release manager approval reference
- rollback trigger: unexpected affected-row count, missing backup/PITR proof, cleanup failure log, or unexplained post-run count delta
- follow-up evidence: rendered cron configuration, scheduler startup log, pre-run query output, post-run cleanup log, post-run query output, backup/PITR proof, and incident record if rollback is invoked

## Rollback And Incident Handling

There is no automatic restore path for rows physically deleted after retention expiry. Before first production enablement:

- confirm backups cover `accounts`, `voiceprints`, `content_audit`, and `safety_audit`
- confirm point-in-time restore is available for the cleanup window
- keep the first production run under an operations change window

If cleanup deletes unexpected rows:

1. Disable the three cron properties in deployment configuration.
2. Preserve manager-api logs and database audit logs.
3. Capture the dry-run queries and affected-row log counts.
4. Restore only through the approved database backup/PITR process.

## Current Local Limitation

This runbook is local delivery evidence and an operations checklist. It does not prove the production scheduler has run. Production evidence must be attached to the release or operations ticket after deployment.
