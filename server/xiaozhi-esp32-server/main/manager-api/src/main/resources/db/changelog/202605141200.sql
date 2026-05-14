-- v2 M2.1: minimal business tables for accounts, devices, bindings, activation, and tasks.

CREATE TABLE IF NOT EXISTS `accounts` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'internal account id',
    `unionid` VARCHAR(64) NOT NULL COMMENT 'wechat unionid',
    `openid` VARCHAR(64) DEFAULT NULL COMMENT 'wechat openid',
    `display_name` VARCHAR(128) DEFAULT NULL COMMENT 'account display name',
    `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active|disabled',
    `primary_session_id` VARCHAR(64) DEFAULT NULL COMMENT 'current primary session id',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_accounts_unionid` (`unionid`),
    KEY `idx_accounts_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 business accounts';

CREATE TABLE IF NOT EXISTS `devices` (
    `id` VARCHAR(64) NOT NULL COMMENT 'device id',
    `device_sn` VARCHAR(64) NOT NULL COMMENT 'device serial number',
    `device_secret` VARCHAR(128) DEFAULT NULL COMMENT 'device secret',
    `model` VARCHAR(64) DEFAULT NULL COMMENT 'device model',
    `hw_rev` VARCHAR(32) DEFAULT NULL COMMENT 'hardware revision',
    `fw_rev` VARCHAR(32) DEFAULT NULL COMMENT 'firmware revision',
    `workspace_mm` JSON DEFAULT NULL COMMENT 'workspace bounds snapshot',
    `status` VARCHAR(16) NOT NULL DEFAULT 'unprovisioned' COMMENT 'unprovisioned|provisioned|bound|retired',
    `last_seen_at` DATETIME DEFAULT NULL COMMENT 'last online time',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_devices_device_sn` (`device_sn`),
    KEY `idx_devices_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 business devices';

CREATE TABLE IF NOT EXISTS `device_bindings` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'binding row id',
    `account_id` BIGINT NOT NULL COMMENT 'bound account id',
    `device_id` VARCHAR(64) NOT NULL COMMENT 'bound device id',
    `binding_status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active|transferred|unbound',
    `is_primary` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'whether current owner binding',
    `bound_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'bind time',
    `unbound_at` DATETIME DEFAULT NULL COMMENT 'unbind time',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_bindings_device_account_status` (`device_id`, `account_id`, `binding_status`),
    KEY `idx_device_bindings_account_status` (`account_id`, `binding_status`),
    KEY `idx_device_bindings_device_status` (`device_id`, `binding_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 account-device bindings';

CREATE TABLE IF NOT EXISTS `activation_codes` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'activation row id',
    `device_sn` VARCHAR(64) NOT NULL COMMENT 'device serial number',
    `device_id` VARCHAR(64) DEFAULT NULL COMMENT 'device id after provisioning',
    `activation_code` VARCHAR(64) NOT NULL COMMENT 'activation code',
    `status` VARCHAR(16) NOT NULL DEFAULT 'unprovisioned' COMMENT 'unprovisioned|provisioned|bound|expired|revoked',
    `expires_at` DATETIME DEFAULT NULL COMMENT 'expiration time',
    `used_at` DATETIME DEFAULT NULL COMMENT 'activation use time',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_activation_codes_device_sn` (`device_sn`),
    UNIQUE KEY `uk_activation_codes_activation_code` (`activation_code`),
    KEY `idx_activation_codes_device_id` (`device_id`),
    KEY `idx_activation_codes_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 device activation codes';

CREATE TABLE IF NOT EXISTS `tasks` (
    `id` VARCHAR(64) NOT NULL COMMENT 'task id',
    `account_id` BIGINT NOT NULL COMMENT 'submitter account id',
    `device_id` VARCHAR(64) NOT NULL COMMENT 'target device id',
    `request_id` VARCHAR(64) DEFAULT NULL COMMENT 'idempotency request id',
    `trace_id` VARCHAR(64) DEFAULT NULL COMMENT 'full-chain trace id',
    `capability` VARCHAR(32) NOT NULL COMMENT 'requested capability',
    `source` VARCHAR(16) NOT NULL DEFAULT 'client' COMMENT 'client|voice|button|system',
    `params_json` JSON DEFAULT NULL COMMENT 'capability params json',
    `constraints_json` JSON DEFAULT NULL COMMENT 'execution constraints json',
    `status` VARCHAR(16) NOT NULL DEFAULT 'accepted' COMMENT 'accepted|running|done|failed|cancelled',
    `error_code` VARCHAR(64) DEFAULT NULL COMMENT 'failure code',
    `error_message` VARCHAR(255) DEFAULT NULL COMMENT 'failure message',
    `result_json` JSON DEFAULT NULL COMMENT 'result payload json',
    `started_at` DATETIME DEFAULT NULL COMMENT 'execution start time',
    `finished_at` DATETIME DEFAULT NULL COMMENT 'execution finish time',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tasks_account_device_request` (`account_id`, `device_id`, `request_id`),
    KEY `idx_tasks_device_status_created` (`device_id`, `status`, `created_at`),
    KEY `idx_tasks_account_created` (`account_id`, `created_at`),
    KEY `idx_tasks_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 business tasks';
