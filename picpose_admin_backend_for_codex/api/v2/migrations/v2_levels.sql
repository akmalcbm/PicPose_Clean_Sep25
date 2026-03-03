-- PicPose V2 XP and levels migration
-- Adds user progression summary and XP event ledger tables.

START TRANSACTION;

CREATE TABLE IF NOT EXISTS user_progress (
    user_id INT NOT NULL,
    xp INT NOT NULL DEFAULT 0,
    level INT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_progress_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS xp_ledger (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    xp_delta INT NOT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    ref_type VARCHAR(40) NOT NULL,
    ref_id VARCHAR(80) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_xp_ledger_user_created_at (user_id, created_at),
    KEY idx_xp_ledger_event_type (event_type),
    KEY idx_xp_ledger_ref (ref_type, ref_id),
    UNIQUE KEY uq_xp_ledger_ref (ref_type, ref_id),
    CONSTRAINT fk_xp_ledger_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

COMMIT;
