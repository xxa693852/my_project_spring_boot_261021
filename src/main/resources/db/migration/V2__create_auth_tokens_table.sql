-- V2：建立登入權杖資料表，只存雜湊值。
CREATE TABLE auth_tokens ( -- 登入權杖紀錄。
    id BIGINT NOT NULL AUTO_INCREMENT, -- 資料編號（主鍵）。
    owner_type VARCHAR(32) NOT NULL, -- 權杖持有者類型。
    owner_id BIGINT NOT NULL, -- 權杖持有者編號。
    access_token_hash VARCHAR(64) NOT NULL, -- 存取權杖雜湊值（不儲存原始權杖）。
    refresh_token_hash VARCHAR(64) NOT NULL, -- 更新權杖雜湊值（不儲存原始權杖）。
    access_expires_at DATETIME(6) NOT NULL, -- 存取權杖到期時間。
    refresh_expires_at DATETIME(6) NOT NULL, -- 更新權杖到期時間。
    revoked TINYINT NOT NULL DEFAULT 0, -- 是否已撤銷（0：否；1：是）。
    created_at DATETIME(6) NOT NULL, -- 建立時間。
    updated_at DATETIME(6) NOT NULL, -- 最後更新時間。
    deleted_at DATETIME(6) NULL, -- 軟刪除時間（空值表示尚未刪除）。
    PRIMARY KEY (id), -- 資料編號為主鍵。
    UNIQUE KEY uk_auth_tokens_access_token_hash (access_token_hash), -- 存取權杖雜湊值不可重複。
    UNIQUE KEY uk_auth_tokens_refresh_token_hash (refresh_token_hash), -- 更新權杖雜湊值不可重複。
    KEY idx_auth_tokens_owner (owner_type, owner_id), -- 加速依持有者類型與編號查詢。
    KEY idx_auth_tokens_refresh_expired (refresh_expires_at) -- 加速查詢更新權杖到期時間。
) ENGINE = InnoDB -- 使用支援交易與外鍵的 InnoDB 儲存引擎。
  DEFAULT CHARACTER SET = utf8mb4 -- 使用支援中文與表情符號的字元集。
  COLLATE = utf8mb4_unicode_ci; -- 使用 Unicode 排序規則，比對時不區分大小寫。
