CREATE TABLE IF NOT EXISTS `device_transfer_requests` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'device transfer request id',
    `device_id` VARCHAR(64) NOT NULL COMMENT 'device id to transfer',
    `source_account_id` BIGINT NOT NULL COMMENT 'current owner account id',
    `target_account_id` BIGINT NOT NULL COMMENT 'target account id',
    `target_unionid` VARCHAR(64) NOT NULL COMMENT 'target wechat unionid',
    `status` VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending|accepted|cancelled',
    `requested_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'request creation time',
    `accepted_at` DATETIME DEFAULT NULL COMMENT 'request accept time',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`id`),
    KEY `idx_device_transfer_source_status` (`source_account_id`, `status`),
    KEY `idx_device_transfer_target_status` (`target_account_id`, `status`),
    KEY `idx_device_transfer_device_status` (`device_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 two-party device transfer requests';
