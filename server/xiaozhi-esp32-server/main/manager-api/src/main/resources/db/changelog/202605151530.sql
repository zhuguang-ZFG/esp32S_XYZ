-- v2 M3.5: audit BusinessServer safety preflight rejections.

CREATE TABLE IF NOT EXISTS `safety_audit` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'audit row id',
    `account_id` BIGINT DEFAULT NULL COMMENT 'account id that submitted the rejected request',
    `device_id` VARCHAR(64) NOT NULL COMMENT 'target device id',
    `capability` VARCHAR(32) NOT NULL COMMENT 'capability rejected by BusinessServer safety preflight',
    `reason` VARCHAR(255) NOT NULL COMMENT 'safety rejection code and reason',
    `ts` DATETIME NOT NULL COMMENT 'rejection timestamp',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    PRIMARY KEY (`id`),
    KEY `idx_safety_audit_device_ts` (`device_id`, `ts`),
    KEY `idx_safety_audit_account_ts` (`account_id`, `ts`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 safety decision audit';
