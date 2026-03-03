-- PicPose V2 personalization migration
-- Adds per-user tag affinity scoring storage for recommendation signals.

START TRANSACTION;

CREATE TABLE IF NOT EXISTS user_tag_scores (
    user_id INT NOT NULL,
    tag VARCHAR(64) NOT NULL,
    score INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, tag),
    KEY idx_user_tag_scores_user_score (user_id, score),
    CONSTRAINT fk_user_tag_scores_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

COMMIT;
