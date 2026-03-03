-- PicPose V2 referrals migration
-- Adds referral code ownership and referee/referrer linkage tracking.

START TRANSACTION;

CREATE TABLE IF NOT EXISTS referral_codes (
    user_id INT NOT NULL,
    code VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_referral_codes_code (code),
    CONSTRAINT fk_referral_codes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS referrals (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    referrer_id INT NOT NULL,
    referee_id INT NOT NULL,
    status ENUM('PENDING', 'QUALIFIED', 'REWARDED') NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_referrals_referee_id (referee_id),
    KEY idx_referrals_referrer_created_at (referrer_id, created_at),
    KEY idx_referrals_status (status),
    CONSTRAINT fk_referrals_referrer FOREIGN KEY (referrer_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_referrals_referee FOREIGN KEY (referee_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

COMMIT;
