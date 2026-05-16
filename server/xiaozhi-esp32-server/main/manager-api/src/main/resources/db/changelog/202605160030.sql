CREATE TABLE IF NOT EXISTS `device_supplies` (
    `device_id` VARCHAR(64) NOT NULL COMMENT 'device id',
    `pen_installed_at` DATETIME DEFAULT NULL COMMENT 'user-marked latest pen replacement time',
    `pen_ink_percent_est` INT DEFAULT NULL COMMENT 'estimated pen ink percent, not sensor measured',
    `pen_mileage_mm` DECIMAL(12, 2) NOT NULL DEFAULT 0 COMMENT 'estimated path mileage since latest pen replacement',
    `paper_slot_state` VARCHAR(16) NOT NULL DEFAULT 'unknown' COMMENT 'empty|loaded|unknown',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`device_id`),
    KEY `idx_device_supplies_paper_state` (`paper_slot_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 manual device consumable state';
