-- PicPose V2 premium packs migration
-- Adds premium bundle definitions, bundle contents, and per-user pack unlocks.

START TRANSACTION;

CREATE TABLE IF NOT EXISTS premium_packs (
    id INT NOT NULL AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    description TEXT NULL,
    price_points INT NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_premium_packs_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS premium_pack_items (
    pack_id INT NOT NULL,
    post_id INT NOT NULL,
    PRIMARY KEY (pack_id, post_id),
    KEY idx_premium_pack_items_post_id (post_id),
    CONSTRAINT fk_premium_pack_items_pack FOREIGN KEY (pack_id) REFERENCES premium_packs(id) ON DELETE CASCADE,
    CONSTRAINT fk_premium_pack_items_post FOREIGN KEY (post_id) REFERENCES ai_posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_pack_unlocks (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    pack_id INT NOT NULL,
    unlock_type ENUM('POINTS', 'IAP', 'SUBSCRIPTION', 'ADMIN') NOT NULL,
    points_spent INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_pack_unlocks_user_pack (user_id, pack_id),
    KEY idx_user_pack_unlocks_pack_id (pack_id),
    CONSTRAINT fk_user_pack_unlocks_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_pack_unlocks_pack FOREIGN KEY (pack_id) REFERENCES premium_packs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

COMMIT;
