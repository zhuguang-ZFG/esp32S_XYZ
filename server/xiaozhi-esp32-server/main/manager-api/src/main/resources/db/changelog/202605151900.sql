-- v2 M6.2: privacy deletion audit retention fields.

ALTER TABLE `accounts`
    ADD COLUMN `deleted_at` DATETIME DEFAULT NULL COMMENT 'soft deletion time' AFTER `primary_session_id`,
    ADD COLUMN `audit_retain_until` DATETIME DEFAULT NULL COMMENT 'minimum audit retention deadline' AFTER `deleted_at`,
    ADD KEY `idx_accounts_deleted_at` (`deleted_at`);

ALTER TABLE `voiceprints`
    ADD COLUMN `deleted_at` DATETIME DEFAULT NULL COMMENT 'soft deletion time' AFTER `expires_at`,
    ADD COLUMN `audit_retain_until` DATETIME DEFAULT NULL COMMENT 'minimum audit retention deadline' AFTER `deleted_at`,
    ADD KEY `idx_voiceprints_deleted_at` (`deleted_at`);
