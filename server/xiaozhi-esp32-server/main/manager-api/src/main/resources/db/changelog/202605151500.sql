-- v2 M3.5: cache minimal device runtime state for BusinessServer safety preflight.

ALTER TABLE `devices`
    ADD COLUMN `runtime_state` VARCHAR(16) DEFAULT NULL COMMENT 'latest runtime state: IDLE/RUNNING/HOMING/PAUSED/ALARM/ERROR/ESTOP' AFTER `status`,
    ADD COLUMN `homed` TINYINT(1) DEFAULT NULL COMMENT 'latest homed flag reported by device runtime status' AFTER `runtime_state`,
    ADD COLUMN `position_mm` JSON DEFAULT NULL COMMENT 'latest runtime position snapshot' AFTER `homed`,
    ADD COLUMN `runtime_seen_at` DATETIME DEFAULT NULL COMMENT 'latest runtime status timestamp' AFTER `position_mm`;

CREATE INDEX `idx_devices_runtime_seen_at` ON `devices` (`runtime_seen_at`);
