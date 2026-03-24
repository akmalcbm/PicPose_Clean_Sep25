-- PicPose V2 prompt access controls + rewarded unlock receipts
-- Adds explicit access flags on ai_posts and a dedicated receipt table for rewarded prompt unlocks.

START TRANSACTION;

SET @db_name = DATABASE();

-- ai_posts.is_visible_in_general_feed
SET @sql_stmt = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'ai_posts'
              AND COLUMN_NAME = 'is_visible_in_general_feed'
        ),
        'SELECT 1',
        'ALTER TABLE ai_posts ADD COLUMN is_visible_in_general_feed TINYINT(1) NULL DEFAULT NULL AFTER premium_pack'
    )
);
PREPARE stmt FROM @sql_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ai_posts.credit_unlock_enabled
SET @sql_stmt = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'ai_posts'
              AND COLUMN_NAME = 'credit_unlock_enabled'
        ),
        'SELECT 1',
        'ALTER TABLE ai_posts ADD COLUMN credit_unlock_enabled TINYINT(1) NULL DEFAULT NULL AFTER is_visible_in_general_feed'
    )
);
PREPARE stmt FROM @sql_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ai_posts.reward_unlock_enabled
SET @sql_stmt = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'ai_posts'
              AND COLUMN_NAME = 'reward_unlock_enabled'
        ),
        'SELECT 1',
        'ALTER TABLE ai_posts ADD COLUMN reward_unlock_enabled TINYINT(1) NULL DEFAULT NULL AFTER credit_unlock_enabled'
    )
);
PREPARE stmt FROM @sql_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ai_posts.token_unlock_enabled
SET @sql_stmt = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'ai_posts'
              AND COLUMN_NAME = 'token_unlock_enabled'
        ),
        'SELECT 1',
        'ALTER TABLE ai_posts ADD COLUMN token_unlock_enabled TINYINT(1) NULL DEFAULT NULL AFTER reward_unlock_enabled'
    )
);
PREPARE stmt FROM @sql_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ai_posts.subscriber_unlock_enabled
SET @sql_stmt = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'ai_posts'
              AND COLUMN_NAME = 'subscriber_unlock_enabled'
        ),
        'SELECT 1',
        'ALTER TABLE ai_posts ADD COLUMN subscriber_unlock_enabled TINYINT(1) NULL DEFAULT NULL AFTER token_unlock_enabled'
    )
);
PREPARE stmt FROM @sql_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Index for visibility lookups
SET @sql_stmt = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = @db_name
              AND TABLE_NAME = 'ai_posts'
              AND INDEX_NAME = 'idx_ai_posts_general_visibility'
        ),
        'SELECT 1',
        'ALTER TABLE ai_posts ADD INDEX idx_ai_posts_general_visibility (is_visible_in_general_feed)'
    )
);
PREPARE stmt FROM @sql_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Dedicated table to avoid AD_UNLOCK collisions in user_daily_claims unique key.
CREATE TABLE IF NOT EXISTS user_prompt_ad_unlock_receipts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    post_id INT NOT NULL,
    ad_reward_id VARCHAR(80) NOT NULL,
    claim_date DATE NOT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_prompt_ad_receipt (user_id, ad_reward_id),
    KEY idx_user_prompt_ad_receipt_daily (user_id, claim_date),
    KEY idx_user_prompt_ad_receipt_post (post_id),
    CONSTRAINT fk_user_prompt_ad_receipt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_prompt_ad_receipt_post FOREIGN KEY (post_id) REFERENCES ai_posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

COMMIT;
