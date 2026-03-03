-- PicPose V2 premium, wallet, and streak migration
-- Creates monetization + unlock + streak tables and extends ai_posts metadata.

START TRANSACTION;

ALTER TABLE ai_posts
    ADD COLUMN tier ENUM('FREE', 'PREMIUM') NOT NULL DEFAULT 'FREE' AFTER is_featured,
    ADD COLUMN premium_unlock_cost_points INT NOT NULL DEFAULT 0 AFTER tier,
    ADD COLUMN premium_pack VARCHAR(40) NULL AFTER premium_unlock_cost_points,
    ADD INDEX idx_ai_posts_tier (tier),
    ADD INDEX idx_ai_posts_pack (premium_pack);

CREATE TABLE IF NOT EXISTS user_wallet (
    user_id INT NOT NULL,
    points_balance BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_wallet_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS points_ledger (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    type VARCHAR(40) NOT NULL,
    delta_points BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    ref_type VARCHAR(40) NOT NULL,
    ref_id VARCHAR(80) NOT NULL,
    meta_json JSON NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_points_ledger_user_created_at (user_id, created_at),
    KEY idx_points_ledger_ref (ref_type, ref_id),
    UNIQUE KEY uq_points_ledger_ref (ref_type, ref_id),
    CONSTRAINT fk_points_ledger_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_prompt_unlocks (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    post_id INT NOT NULL,
    unlock_type ENUM('AD', 'POINTS', 'SUBSCRIPTION', 'ADMIN') NOT NULL,
    points_spent BIGINT NOT NULL DEFAULT 0,
    ref_type VARCHAR(40) NULL,
    ref_id VARCHAR(80) NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_prompt_unlocks_user_post (user_id, post_id),
    KEY idx_user_prompt_unlocks_post (post_id),
    CONSTRAINT fk_user_prompt_unlocks_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_prompt_unlocks_post FOREIGN KEY (post_id) REFERENCES ai_posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_streaks (
    user_id INT NOT NULL,
    streak_count INT NOT NULL DEFAULT 0,
    last_claim_date DATE NULL,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_streaks_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_daily_claims (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    claim_date DATE NOT NULL,
    claim_type VARCHAR(40) NOT NULL,
    ref_id VARCHAR(80) NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_daily_claims (user_id, claim_date, claim_type),
    KEY idx_user_daily_claims_user_created_at (user_id, created_at),
    CONSTRAINT fk_user_daily_claims_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

COMMIT;
