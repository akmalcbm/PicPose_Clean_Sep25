-- PicPose V2 Prompt of the Day migration
-- Supports free or discounted premium prompt access by server-local date.

START TRANSACTION;

CREATE TABLE IF NOT EXISTS daily_featured_prompts (
    day_date DATE NOT NULL,
    post_id INT NOT NULL,
    mode ENUM('FREE', 'DISCOUNT', 'NORMAL') NOT NULL DEFAULT 'NORMAL',
    discount_cost_points INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (day_date),
    KEY idx_daily_featured_prompts_post_id (post_id),
    KEY idx_daily_featured_prompts_mode (mode),
    CONSTRAINT fk_daily_featured_prompts_post FOREIGN KEY (post_id) REFERENCES ai_posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

COMMIT;
