-- v2 M4.5: minimal family members and voiceprint enrollment metadata.

CREATE TABLE IF NOT EXISTS `members` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'member row id',
    `account_id` BIGINT NOT NULL COMMENT 'owning account id',
    `device_id` VARCHAR(64) NOT NULL COMMENT 'bound device id',
    `display_name` VARCHAR(128) NOT NULL COMMENT 'family member display name',
    `role` VARCHAR(32) NOT NULL DEFAULT 'owner' COMMENT 'owner|member|child',
    `member_type` VARCHAR(32) NOT NULL DEFAULT 'owner' COMMENT 'owner|member|child',
    `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active|disabled|deleted',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`id`),
    KEY `idx_members_account_device` (`account_id`, `device_id`),
    KEY `idx_members_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 family members';

CREATE TABLE IF NOT EXISTS `voiceprints` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'voiceprint row id',
    `account_id` BIGINT NOT NULL COMMENT 'owning account id',
    `member_id` BIGINT NOT NULL COMMENT 'member id',
    `device_id` VARCHAR(64) NOT NULL COMMENT 'bound device id',
    `provider` VARCHAR(64) NOT NULL COMMENT 'voiceprint provider key',
    `speaker_ref` VARCHAR(128) NOT NULL COMMENT 'provider speaker reference',
    `embedding_hash` CHAR(64) NOT NULL COMMENT 'sha256 hash of processed enrollment audio or embedding; raw audio is not stored',
    `sample_duration_ms` INT NOT NULL COMMENT 'enrollment audio duration in milliseconds',
    `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active|disabled|deleted',
    `enrolled_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'enrollment time',
    `expires_at` DATETIME DEFAULT NULL COMMENT 'optional re-enrollment deadline',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_voiceprints_speaker_ref` (`speaker_ref`),
    KEY `idx_voiceprints_member` (`member_id`),
    KEY `idx_voiceprints_account_device` (`account_id`, `device_id`),
    KEY `idx_voiceprints_status` (`status`),
    KEY `idx_voiceprints_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 voiceprint enrollment metadata';
