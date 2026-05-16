-- v2 M3.7: audit blocked content moderation decisions without storing raw content.

CREATE TABLE IF NOT EXISTS `content_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'audit row id',
    `account_id` BIGINT DEFAULT NULL COMMENT 'account id that submitted the blocked request',
    `device_id` VARCHAR(64) NOT NULL COMMENT 'target device id',
    `path` VARCHAR(64) NOT NULL COMMENT 'content path, for example write_text.text',
    `raw_hash` CHAR(64) NOT NULL COMMENT 'SHA-256 hash of the blocked raw content',
    `rule_hit` VARCHAR(255) NOT NULL COMMENT 'moderation rule or provider reason',
    `ts` DATETIME NOT NULL COMMENT 'moderation timestamp',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    PRIMARY KEY (`id`),
    KEY `idx_content_audit_device_ts` (`device_id`, `ts`),
    KEY `idx_content_audit_account_ts` (`account_id`, `ts`),
    KEY `idx_content_audit_path_ts` (`path`, `ts`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 blocked content moderation audit';
