-- v2 M6.4: task status width for primary voice approval workflow.

ALTER TABLE `tasks`
    MODIFY COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'accepted' COMMENT 'accepted|pending_primary_approval|running|done|failed|cancelled|rejected';
