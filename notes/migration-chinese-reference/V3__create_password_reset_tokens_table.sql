-- V3：建立一次性密碼重設權杖資料表。
CREATE TABLE password_reset_tokens ( -- 密碼重設權杖。
    id BIGINT NOT NULL AUTO_INCREMENT, -- 資料編號（主鍵）。
    member_id BIGINT NOT NULL, -- 會員編號（對應 members.id）。
    token_hash VARCHAR(64) NOT NULL, -- 一次性密碼重設權杖的雜湊值。
    expires_at DATETIME(6) NOT NULL, -- 密碼重設權杖到期時間。
    used TINYINT NOT NULL DEFAULT 0, -- 是否已使用（0：否；1：是）。
    created_at DATETIME(6) NOT NULL, -- 建立時間。
    updated_at DATETIME(6) NOT NULL, -- 最後更新時間。
    deleted_at DATETIME(6) NULL, -- 軟刪除時間（空值表示尚未刪除）。
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id), -- 密碼重設權杖的編號為主鍵。
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash), -- 密碼重設權杖雜湊值不可重複。
    CONSTRAINT fk_password_reset_tokens_member -- 重設密碼的會員關聯至 members.id。
        FOREIGN KEY (member_id) REFERENCES members (id) -- member_id 關聯至 members.id。
        ON UPDATE RESTRICT ON DELETE RESTRICT, -- 限制刪除或修改仍被引用的主鍵。
    CONSTRAINT chk_password_reset_tokens_used CHECK (used IN (0, 1)), -- 使用標記只能是 0（未使用）或 1（已使用）。
    INDEX idx_password_reset_tokens_member_used (member_id, used), -- 加速依會員與使用狀態查詢。
    INDEX idx_password_reset_tokens_expires_at (expires_at) -- 加速查詢密碼重設權杖到期時間。
) ENGINE = InnoDB -- 使用支援交易與外鍵的 InnoDB 儲存引擎。
  DEFAULT CHARACTER SET = utf8mb4 -- 使用支援中文與表情符號的字元集。
  COLLATE = utf8mb4_unicode_ci; -- 使用 Unicode 排序規則，比對時不區分大小寫。
