-- v2 M6.4: primary-session lease timestamp.

ALTER TABLE `accounts`
    ADD COLUMN `primary_session_claimed_at` DATETIME DEFAULT NULL COMMENT 'primary session claim time' AFTER `primary_session_id`;
