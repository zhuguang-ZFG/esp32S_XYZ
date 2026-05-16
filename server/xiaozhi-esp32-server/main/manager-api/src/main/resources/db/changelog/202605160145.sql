-- v2 M5.8: persisted self-check diagnostic history.

CREATE TABLE IF NOT EXISTS `device_self_check_events` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'self-check event id',
    `device_id` VARCHAR(64) NOT NULL COMMENT 'device id',
    `check_id` VARCHAR(64) NOT NULL DEFAULT 'startup' COMMENT 'startup|manual|other',
    `scope` VARCHAR(32) NOT NULL DEFAULT 'startup' COMMENT 'startup|manual',
    `status` VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pass|fail|warn|pending',
    `summary` VARCHAR(512) DEFAULT NULL COMMENT 'compact check summary',
    `checks_json` TEXT DEFAULT NULL COMMENT 'raw checks payload json',
    `payload_json` TEXT DEFAULT NULL COMMENT 'raw self_check payload json',
    `reported_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'device reported time',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    PRIMARY KEY (`id`),
    KEY `idx_self_check_device_reported` (`device_id`, `reported_at`),
    KEY `idx_self_check_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 device self-check diagnostic history';
