CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_hash VARCHAR(100) NOT NULL UNIQUE,
    account_type VARCHAR(20) NOT NULL,
    account_id BIGINT NOT NULL,
    email VARCHAR(150) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_password_reset_token_hash (token_hash),
    INDEX idx_password_reset_account (account_type, account_id),
    INDEX idx_password_reset_expires (expires_at)
);
