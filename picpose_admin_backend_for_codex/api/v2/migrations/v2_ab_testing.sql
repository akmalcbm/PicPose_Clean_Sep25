-- PicPose V2 A/B testing migration
-- Adds experiment definitions and stable per-user variant assignments.

START TRANSACTION;

CREATE TABLE IF NOT EXISTS ab_experiments (
    id INT NOT NULL AUTO_INCREMENT,
    key_name VARCHAR(64) NOT NULL,
    variants_json JSON NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ab_experiments_key_name (key_name),
    KEY idx_ab_experiments_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ab_user_assignments (
    user_id INT NOT NULL,
    experiment_key VARCHAR(64) NOT NULL,
    variant VARCHAR(32) NOT NULL,
    assigned_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, experiment_key),
    KEY idx_ab_user_assignments_experiment_key (experiment_key),
    CONSTRAINT fk_ab_user_assignments_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

COMMIT;
