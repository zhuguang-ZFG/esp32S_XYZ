ALTER TABLE `devices`
    MODIFY COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'unprovisioned'
    COMMENT 'unprovisioned|provisioned_unbound|bound|rma_in_progress|returned|disposed';

ALTER TABLE `device_bindings`
    MODIFY COLUMN `binding_status` VARCHAR(32) NOT NULL DEFAULT 'active'
    COMMENT 'active|transferred|unbound|rma_in_progress|returned|disposed';

ALTER TABLE `activation_codes`
    MODIFY COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'unprovisioned'
    COMMENT 'unprovisioned|provisioned|bound|expired|revoked';
