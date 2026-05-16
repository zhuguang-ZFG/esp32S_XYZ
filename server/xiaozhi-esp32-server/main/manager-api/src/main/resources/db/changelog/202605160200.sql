-- v2 M6.4/M6.5: product notification outbox for future platform push delivery.

CREATE TABLE IF NOT EXISTS `product_notification_events` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'notification event id',
    `event_type` VARCHAR(64) NOT NULL COMMENT 'semantic event type',
    `recipient_account_id` BIGINT NOT NULL COMMENT 'account that should receive the notification',
    `device_id` VARCHAR(64) NOT NULL COMMENT 'related device id',
    `target_ref_type` VARCHAR(32) NOT NULL COMMENT 'related object type',
    `target_ref_id` VARCHAR(64) NOT NULL COMMENT 'related object id',
    `deep_link` VARCHAR(255) NOT NULL COMMENT 'safe in-app deep link',
    `status` VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT 'pending|sent|failed|resolved|cancelled',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    `sent_at` DATETIME DEFAULT NULL COMMENT 'provider send time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`id`),
    KEY `idx_product_notification_recipient_status` (`recipient_account_id`, `status`, `created_at`),
    KEY `idx_product_notification_target` (`target_ref_type`, `target_ref_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 product notification outbox';
