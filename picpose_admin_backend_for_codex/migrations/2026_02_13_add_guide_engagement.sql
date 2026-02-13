-- Add server-backed engagement fields for guide posts
ALTER TABLE `guide_posts`
  ADD COLUMN `views` INT NOT NULL DEFAULT 0 AFTER `likes`;

-- Track per-device like state for guides (prevents multiple likes from same device)
CREATE TABLE IF NOT EXISTS `guide_likes` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `guide_id` INT NOT NULL,
  `device_id` VARCHAR(100) NOT NULL,
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_guide_device` (`guide_id`, `device_id`),
  KEY `idx_guide_id` (`guide_id`),
  KEY `idx_device_id` (`device_id`),
  CONSTRAINT `fk_guide_likes_guide_id`
    FOREIGN KEY (`guide_id`) REFERENCES `guide_posts` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
