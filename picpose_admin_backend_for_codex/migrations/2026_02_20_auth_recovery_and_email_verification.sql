-- PicPose V2 auth hardening migration
-- Adds password reset + email verification support (token-hash based)

ALTER TABLE users
    ADD COLUMN email_verified TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN email_verified_at DATETIME NULL;

CREATE TABLE IF NOT EXISTS password_resets (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id INT(11) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    ip VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    request_count INT NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    INDEX idx_password_resets_user_id (user_id),
    INDEX idx_password_resets_expires_at (expires_at),
    INDEX idx_password_resets_used_at (used_at),
    INDEX idx_password_resets_token_hash (token_hash),
    CONSTRAINT fk_password_resets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS password_reset_requests (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    ip VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_password_reset_requests_email_time (email, created_at),
    INDEX idx_password_reset_requests_ip_time (ip, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS email_verifications (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id INT(11) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    ip VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    PRIMARY KEY (id),
    INDEX idx_email_verifications_user_id (user_id),
    INDEX idx_email_verifications_expires_at (expires_at),
    INDEX idx_email_verifications_used_at (used_at),
    INDEX idx_email_verifications_token_hash (token_hash),
    CONSTRAINT fk_email_verifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
