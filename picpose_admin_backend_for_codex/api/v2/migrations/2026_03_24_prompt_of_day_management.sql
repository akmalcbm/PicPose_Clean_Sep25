-- PicPose Prompt of the Day management schema
-- Adds admin-managed scheduled/default Prompt of the Day entries with configurable fallback behavior.

START TRANSACTION;

SET @db_name = DATABASE();

CREATE TABLE IF NOT EXISTS prompt_of_day_entries (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT,
    prompt_id INT NOT NULL,
    title_override VARCHAR(255) DEFAULT NULL,
    subtitle_override VARCHAR(255) DEFAULT NULL,
    badge_text VARCHAR(80) DEFAULT NULL,
    start_date DATE DEFAULT NULL,
    end_date DATE DEFAULT NULL,
    mode ENUM('FREE', 'DISCOUNT', 'NORMAL') NOT NULL DEFAULT 'NORMAL',
    discount_cost_points INT NOT NULL DEFAULT 0,
    priority INT NOT NULL DEFAULT 0,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_by_admin_id INT DEFAULT NULL,
    updated_by_admin_id INT DEFAULT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_potd_entries_prompt_id (prompt_id),
    KEY idx_potd_entries_active_window (is_active, is_default, start_date, end_date, priority),
    KEY idx_potd_entries_default_active (is_default, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS prompt_of_day_config (
    id TINYINT UNSIGNED NOT NULL,
    allow_featured_fallback TINYINT(1) NOT NULL DEFAULT 1,
    allow_premium_prompts TINYINT(1) NOT NULL DEFAULT 1,
    enable_legacy_daily_fallback TINYINT(1) NOT NULL DEFAULT 1,
    featured_fallback_mode ENUM('FREE', 'DISCOUNT', 'NORMAL') NOT NULL DEFAULT 'NORMAL',
    featured_fallback_discount_cost_points INT NOT NULL DEFAULT 0,
    default_badge_text VARCHAR(80) DEFAULT NULL,
    updated_by_admin_id INT DEFAULT NULL,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO prompt_of_day_config (
    id,
    allow_featured_fallback,
    allow_premium_prompts,
    enable_legacy_daily_fallback,
    featured_fallback_mode,
    featured_fallback_discount_cost_points,
    default_badge_text,
    updated_by_admin_id
)
SELECT
    1,
    1,
    1,
    1,
    'NORMAL',
    0,
    'Today\'s Pick',
    NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM prompt_of_day_config
    WHERE id = 1
);

SET @sql_stmt = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.REFERENTIAL_CONSTRAINTS
            WHERE CONSTRAINT_SCHEMA = @db_name
              AND CONSTRAINT_NAME = 'fk_potd_entries_prompt'
              AND TABLE_NAME = 'prompt_of_day_entries'
        ),
        'SELECT 1',
        'ALTER TABLE prompt_of_day_entries ADD CONSTRAINT fk_potd_entries_prompt FOREIGN KEY (prompt_id) REFERENCES ai_posts(id) ON DELETE CASCADE'
    )
);
PREPARE stmt FROM @sql_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

COMMIT;
