-- Login token records; raw tokens are never stored here.
CREATE TABLE auth_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_type VARCHAR(32) NOT NULL,
    owner_id BIGINT NOT NULL,
    access_token_hash VARCHAR(64) NOT NULL,
    refresh_token_hash VARCHAR(64) NOT NULL,
    access_expires_at DATETIME(6) NOT NULL,
    refresh_expires_at DATETIME(6) NOT NULL,
    revoked TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_tokens_access_token_hash (access_token_hash),
    UNIQUE KEY uk_auth_tokens_refresh_token_hash (refresh_token_hash),
    KEY idx_auth_tokens_owner (owner_type, owner_id),
    KEY idx_auth_tokens_refresh_expired (refresh_expires_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
