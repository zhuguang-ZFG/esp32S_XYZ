-- v2 M3.1/M3.2: minimal content resource domain and factory free resources.

CREATE TABLE IF NOT EXISTS `fonts` (
    `font_id` VARCHAR(64) NOT NULL COMMENT 'font resource id',
    `name` VARCHAR(128) NOT NULL COMMENT 'font display name',
    `version` VARCHAR(32) NOT NULL COMMENT 'font version',
    `vendor` VARCHAR(128) NOT NULL COMMENT 'font vendor for license traceability',
    `file_ref` VARCHAR(255) NOT NULL COMMENT 'font file reference',
    `default_free` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether included in the factory free set',
    `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active|revoked|deprecated',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`font_id`),
    KEY `idx_fonts_status` (`status`),
    KEY `idx_fonts_default_free` (`default_free`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 font resources';

CREATE TABLE IF NOT EXISTS `copybooks` (
    `copybook_id` VARCHAR(64) NOT NULL COMMENT 'copybook resource id',
    `title` VARCHAR(128) NOT NULL COMMENT 'copybook title',
    `font_id` VARCHAR(64) NOT NULL COMMENT 'font used by this copybook',
    `text` TEXT NOT NULL COMMENT 'copybook text content',
    `preview` VARCHAR(255) DEFAULT NULL COMMENT 'preview asset reference',
    `price_model` VARCHAR(32) NOT NULL DEFAULT 'factory_free' COMMENT 'factory_free|purchase|subscription',
    `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active|revoked|deprecated',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`copybook_id`),
    KEY `idx_copybooks_font_id` (`font_id`),
    KEY `idx_copybooks_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 copybook resources';

CREATE TABLE IF NOT EXISTS `assets` (
    `asset_id` VARCHAR(64) NOT NULL COMMENT 'asset resource id',
    `title` VARCHAR(128) NOT NULL COMMENT 'asset title',
    `file_ref` VARCHAR(255) NOT NULL COMMENT 'single-line SVG or asset file reference',
    `preview` VARCHAR(255) DEFAULT NULL COMMENT 'preview asset reference',
    `price_model` VARCHAR(32) NOT NULL DEFAULT 'factory_free' COMMENT 'factory_free|purchase|subscription',
    `fallback_only` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'must not be used as implicit AI fallback when 0',
    `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active|revoked|deprecated',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`asset_id`),
    KEY `idx_assets_status` (`status`),
    KEY `idx_assets_price_model` (`price_model`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 explicit-only drawing assets';

CREATE TABLE IF NOT EXISTS `ai_plans` (
    `plan_id` VARCHAR(64) NOT NULL COMMENT 'AI plan resource id',
    `name` VARCHAR(128) NOT NULL COMMENT 'AI plan display name',
    `provider` VARCHAR(64) NOT NULL COMMENT 'provider key',
    `price_model` VARCHAR(32) NOT NULL DEFAULT 'factory_free' COMMENT 'factory_free|purchase|subscription',
    `default_free` TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether included in the factory free set',
    `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active|revoked|deprecated',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`plan_id`),
    KEY `idx_ai_plans_provider` (`provider`),
    KEY `idx_ai_plans_status` (`status`),
    KEY `idx_ai_plans_default_free` (`default_free`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 AI generation plans';

CREATE TABLE IF NOT EXISTS `entitlements` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'entitlement row id',
    `account_id` BIGINT NOT NULL COMMENT 'account id that owns this entitlement',
    `resource_type` VARCHAR(32) NOT NULL COMMENT 'font|copybook|asset|ai_plan',
    `resource_id` VARCHAR(64) NOT NULL COMMENT 'resource id within its resource type',
    `source` VARCHAR(32) NOT NULL COMMENT 'factory|purchase|subscription|gift|ops',
    `expires_at` DATETIME DEFAULT NULL COMMENT 'null means no expiration',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'creation time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_entitlements_account_resource` (`account_id`, `resource_type`, `resource_id`),
    KEY `idx_entitlements_resource` (`resource_type`, `resource_id`),
    KEY `idx_entitlements_account_source` (`account_id`, `source`),
    KEY `idx_entitlements_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='v2 account resource entitlements';

INSERT INTO `fonts` (`font_id`, `name`, `version`, `vendor`, `file_ref`, `default_free`, `status`)
VALUES ('kai_basic_v1', 'Basic Kai', 'v1', 'factory', 'builtin://fonts/kai_basic_v1', 1, 'active')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `version` = VALUES(`version`),
    `vendor` = VALUES(`vendor`),
    `file_ref` = VALUES(`file_ref`),
    `default_free` = VALUES(`default_free`),
    `status` = VALUES(`status`);

INSERT INTO `copybooks` (`copybook_id`, `title`, `font_id`, `text`, `preview`, `price_model`, `status`)
VALUES ('pinyin_basic_v1', 'Pinyin Basic', 'kai_basic_v1', '你好', 'builtin://copybooks/pinyin_basic_v1/preview', 'factory_free', 'active')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`),
    `font_id` = VALUES(`font_id`),
    `text` = VALUES(`text`),
    `preview` = VALUES(`preview`),
    `price_model` = VALUES(`price_model`),
    `status` = VALUES(`status`);

INSERT INTO `assets` (`asset_id`, `title`, `file_ref`, `preview`, `price_model`, `fallback_only`, `status`)
VALUES
    ('starter_star', 'Starter Star', 'builtin://assets/starter_star.svg', 'builtin://assets/starter_star.preview.svg', 'factory_free', 0, 'active'),
    ('starter_house', 'Starter House', 'builtin://assets/starter_house.svg', 'builtin://assets/starter_house.preview.svg', 'factory_free', 0, 'active'),
    ('starter_tree', 'Starter Tree', 'builtin://assets/starter_tree.svg', 'builtin://assets/starter_tree.preview.svg', 'factory_free', 0, 'active'),
    ('starter_fish', 'Starter Fish', 'builtin://assets/starter_fish.svg', 'builtin://assets/starter_fish.preview.svg', 'factory_free', 0, 'active'),
    ('starter_flower', 'Starter Flower', 'builtin://assets/starter_flower.svg', 'builtin://assets/starter_flower.preview.svg', 'factory_free', 0, 'active')
ON DUPLICATE KEY UPDATE
    `title` = VALUES(`title`),
    `file_ref` = VALUES(`file_ref`),
    `preview` = VALUES(`preview`),
    `price_model` = VALUES(`price_model`),
    `fallback_only` = VALUES(`fallback_only`),
    `status` = VALUES(`status`);

INSERT INTO `ai_plans` (`plan_id`, `name`, `provider`, `price_model`, `default_free`, `status`)
VALUES ('local_fake_ai_plan_v1', 'Local Fake AI Plan', 'local_fake_ai', 'factory_free', 1, 'active')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `provider` = VALUES(`provider`),
    `price_model` = VALUES(`price_model`),
    `default_free` = VALUES(`default_free`),
    `status` = VALUES(`status`);
