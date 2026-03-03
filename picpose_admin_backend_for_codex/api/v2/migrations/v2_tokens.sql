-- PicPose V2 token balances migration
-- Adds per-user consumable token balances for subscription value tiers.

START TRANSACTION;

CREATE TABLE IF NOT EXISTS user_tokens (
    user_id INT NOT NULL,
    token_type ENUM('PROMPT_UNLOCK', 'IMAGE_GEN_PRIORITY') NOT NULL,
    balance INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, token_type),
    KEY idx_user_tokens_token_type (token_type),
    CONSTRAINT fk_user_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

COMMIT;
