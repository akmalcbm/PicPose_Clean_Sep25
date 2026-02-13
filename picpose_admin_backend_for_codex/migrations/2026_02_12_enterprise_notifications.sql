-- Enterprise notifications migration
-- Run on production during low-traffic window.

ALTER TABLE `device_tokens`
  ADD COLUMN `token` varchar(512) NULL AFTER `fcm_token`,
  ADD COLUMN `platform` varchar(50) NOT NULL DEFAULT 'android' AFTER `token`,
  ADD COLUMN `language` varchar(16) NULL AFTER `app_version`,
  ADD COLUMN `country` varchar(8) NULL AFTER `language`,
  ADD COLUMN `timezone` varchar(64) NULL AFTER `country`,
  ADD COLUMN `last_seen_at` datetime NULL AFTER `timezone`,
  ADD COLUMN `updated_at` timestamp NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() AFTER `created_at`;

UPDATE `device_tokens`
SET
  `token` = COALESCE(NULLIF(`token`, ''), `fcm_token`),
  `platform` = COALESCE(NULLIF(`platform`, ''), NULLIF(`device_type`, ''), 'android'),
  `last_seen_at` = COALESCE(`last_seen_at`, `last_active`);

ALTER TABLE `device_tokens`
  MODIFY `token` varchar(512) NOT NULL,
  ADD UNIQUE KEY `uq_device_tokens_token` (`token`),
  ADD KEY `idx_device_tokens_user_active` (`user_id`, `is_active`),
  ADD KEY `idx_device_tokens_last_seen` (`last_seen_at`);

CREATE TABLE `notification_campaigns` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `body` text NOT NULL,
  `image_url` varchar(500) DEFAULT NULL,
  `deep_link` varchar(500) DEFAULT NULL,
  `target_type` enum('all','topic','token') NOT NULL DEFAULT 'all',
  `topic_name` varchar(190) DEFAULT NULL,
  `status` enum('draft','sent','failed') NOT NULL DEFAULT 'draft',
  `scheduled_at` datetime DEFAULT NULL,
  `created_by` int(11) DEFAULT NULL,
  `success_count` int(11) NOT NULL DEFAULT 0,
  `failure_count` int(11) NOT NULL DEFAULT 0,
  `sent_at` datetime DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_campaign_status_created` (`status`, `created_at`),
  KEY `idx_campaign_target_type` (`target_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `notification_logs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `campaign_id` int(11) NOT NULL,
  `token` varchar(512) NOT NULL,
  `success` tinyint(1) NOT NULL DEFAULT 0,
  `fcm_message_id` varchar(255) DEFAULT NULL,
  `error_code` varchar(100) DEFAULT NULL,
  `error_message` text DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_notification_logs_campaign` (`campaign_id`),
  KEY `idx_notification_logs_success` (`success`),
  CONSTRAINT `fk_notification_logs_campaign`
    FOREIGN KEY (`campaign_id`) REFERENCES `notification_campaigns` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
