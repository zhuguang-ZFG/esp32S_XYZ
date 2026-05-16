-- v2 M6.6: persist actual dispatched motion payload for consumables mileage accounting.

ALTER TABLE `tasks`
    ADD COLUMN `dispatch_capability` VARCHAR(32) DEFAULT NULL COMMENT 'actual capability dispatched to DeviceServer' AFTER `constraints_json`,
    ADD COLUMN `dispatch_params_json` JSON DEFAULT NULL COMMENT 'actual params dispatched to DeviceServer' AFTER `dispatch_capability`;
