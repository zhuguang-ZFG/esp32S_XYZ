-- v2 M5.5: minimal OTA release plan domain.

CREATE TABLE IF NOT EXISTS `firmware_releases` (
    `release_id` VARCHAR(64) NOT NULL COMMENT 'firmware release id',
    `channel` VARCHAR(32) NOT NULL DEFAULT 'dev' COMMENT 'dev|stable|canary',
    `version` VARCHAR(32) NOT NULL COMMENT 'firmware version',
    `url` VARCHAR(512) NOT NULL COMMENT 'HTTPS firmware binary URL',
    `sha256` CHAR(64) NOT NULL COMMENT 'lowercase hex sha256 of firmware binary',
    `signature` TEXT NOT NULL COMMENT 'release signature metadata; real verification is enforced by client/server contract',
    `rollout_percent` INT NOT NULL DEFAULT 10 COMMENT 'rollout bucket: 10|50|100',
    `failure_threshold_percent` INT NOT NULL DEFAULT 20 COMMENT 'auto pause when failures/install_count reaches this percent',
    `install_count` INT NOT NULL DEFAULT 0 COMMENT 'reported install attempts',
    `failure_count` INT NOT NULL DEFAULT 0 COMMENT 'reported failed installs',
    `status` VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT 'draft|published|paused|retired',
    `published_at` DATETIME DEFAULT NULL COMMENT 'publish time',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`release_id`),
    KEY `idx_firmware_releases_channel_status` (`channel`, `status`, `published_at`),
    KEY `idx_firmware_releases_version` (`version`),
    KEY `idx_firmware_releases_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 firmware releases and rollout plans';
