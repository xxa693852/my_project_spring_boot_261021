-- V1：建立會員、活動與報名三張核心資料表。
-- D&D 活動報名系統的核心資料表。
-- MySQL 8.0.16+ 才會實際執行 CHECK constraints。

CREATE TABLE members ( -- 會員資料。
    id            BIGINT       NOT NULL AUTO_INCREMENT, -- 資料編號（主鍵）。
    account       VARCHAR(50)  NOT NULL, -- 登入帳號。
    password_hash VARCHAR(255) NOT NULL, -- 登入密碼雜湊值（非明文密碼）。
    name          VARCHAR(50)  NOT NULL, -- 會員姓名。
    email         VARCHAR(254) NOT NULL, -- 電子郵件地址。
    role          VARCHAR(20)  NOT NULL DEFAULT 'MEMBER', -- 會員角色（MEMBER：一般會員；ADMIN：管理員）。
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- 會員狀態（ACTIVE：啟用；DISABLED：停用）。
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6), -- 建立時間。
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) -- 最後更新時間。
                                 ON UPDATE CURRENT_TIMESTAMP(6), -- 資料變動時自動更新時間。

    CONSTRAINT pk_members PRIMARY KEY (id), -- 會員資料的編號為主鍵。
    CONSTRAINT uk_members_account UNIQUE (account), -- 登入帳號不可重複。
    CONSTRAINT uk_members_email UNIQUE (email), -- 電子郵件不可重複。
    CONSTRAINT chk_members_role CHECK (role IN ('MEMBER', 'ADMIN')), -- 角色只允許一般會員或管理員。
    CONSTRAINT chk_members_status CHECK (status IN ('ACTIVE', 'DISABLED')), -- 會員狀態只允許啟用或停用。
    CONSTRAINT chk_members_account_not_blank CHECK (CHAR_LENGTH(TRIM(account)) > 0), -- 登入帳號不得為空白。
    CONSTRAINT chk_members_name_not_blank CHECK (CHAR_LENGTH(TRIM(name)) > 0), -- 會員姓名不得為空白。
    CONSTRAINT chk_members_email_not_blank CHECK (CHAR_LENGTH(TRIM(email)) > 0) -- 電子郵件不得為空白。
) ENGINE = InnoDB -- 使用支援交易與外鍵的 InnoDB 儲存引擎。
  DEFAULT CHARACTER SET = utf8mb4 -- 使用支援中文與表情符號的字元集。
  COLLATE = utf8mb4_unicode_ci; -- 使用 Unicode 排序規則，比對時不區分大小寫。

CREATE TABLE events ( -- 活動資料。
    id                    BIGINT       NOT NULL AUTO_INCREMENT, -- 資料編號（主鍵）。
    title                 VARCHAR(100) NOT NULL, -- 活動名稱。
    description           TEXT         NOT NULL, -- 活動詳細介紹。
    host_name             VARCHAR(50)  NOT NULL, -- 主持人名稱。
    location              VARCHAR(255) NOT NULL, -- 活動地點。
    start_at              DATETIME(6)  NOT NULL, -- 活動開始時間。
    end_at                DATETIME(6)  NOT NULL, -- 活動結束時間。
    registration_deadline DATETIME(6)  NOT NULL, -- 報名截止時間。
    capacity              INT          NOT NULL, -- 報名人數上限。
    beginner_friendly     BOOLEAN      NOT NULL DEFAULT FALSE, -- 是否適合新手（0：否；1：是）。
    status                VARCHAR(20)  NOT NULL DEFAULT 'OPEN', -- 活動狀態（OPEN：開放報名；CLOSED：關閉報名；CANCELLED：已取消）。
    created_by            BIGINT       NOT NULL, -- 建立活動的會員編號（對應 members.id；V15 改名為 member_id）。
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6), -- 建立時間。
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) -- 最後更新時間。
                                       ON UPDATE CURRENT_TIMESTAMP(6), -- 資料變動時自動更新時間。

    CONSTRAINT pk_events PRIMARY KEY (id), -- 活動資料的編號為主鍵。
    CONSTRAINT fk_events_created_by -- 活動建立者關聯至 members.id。
        FOREIGN KEY (created_by) REFERENCES members (id) -- created_by 關聯至 members.id。
        ON UPDATE RESTRICT ON DELETE RESTRICT, -- 限制刪除或修改仍被引用的主鍵。
    CONSTRAINT chk_events_status CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED')), -- 活動狀態只允許開放、關閉或取消。
    CONSTRAINT chk_events_capacity CHECK (capacity > 0), -- 報名人數上限必須大於零。
    CONSTRAINT chk_events_time_order CHECK (end_at > start_at), -- 結束時間必須晚於開始時間。
    CONSTRAINT chk_events_registration_deadline -- 報名截止時間必須早於活動開始時間。
        CHECK (registration_deadline < start_at),
    CONSTRAINT chk_events_title_not_blank CHECK (CHAR_LENGTH(TRIM(title)) > 0), -- 活動名稱不得為空白。
    CONSTRAINT chk_events_host_name_not_blank CHECK (CHAR_LENGTH(TRIM(host_name)) > 0), -- 主持人名稱不得為空白。
    CONSTRAINT chk_events_location_not_blank CHECK (CHAR_LENGTH(TRIM(location)) > 0), -- 活動地點不得為空白。

    INDEX idx_events_status_start_at (status, start_at), -- 加速依活動狀態與開始時間查詢。
    INDEX idx_events_created_by (created_by) -- 加速查詢會員建立的活動。
) ENGINE = InnoDB -- 使用支援交易與外鍵的 InnoDB 儲存引擎。
  DEFAULT CHARACTER SET = utf8mb4 -- 使用支援中文與表情符號的字元集。
  COLLATE = utf8mb4_unicode_ci; -- 使用 Unicode 排序規則，比對時不區分大小寫。

CREATE TABLE registrations ( -- 活動報名紀錄。
    id            BIGINT      NOT NULL AUTO_INCREMENT, -- 資料編號（主鍵）。
    member_id     BIGINT      NOT NULL, -- 會員編號（對應 members.id）。
    event_id      BIGINT      NOT NULL, -- 活動編號（對應 events.id）。
    status        VARCHAR(20) NOT NULL DEFAULT 'REGISTERED', -- 報名狀態（REGISTERED：已報名；CANCELLED：已取消；V13 新增待付款狀態）。
    registered_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), -- 報名時間。
    cancelled_at  DATETIME(6) NULL, -- 取消報名時間。
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), -- 建立時間。
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) -- 最後更新時間。
                              ON UPDATE CURRENT_TIMESTAMP(6), -- 資料變動時自動更新時間。

    CONSTRAINT pk_registrations PRIMARY KEY (id), -- 活動報名紀錄的編號為主鍵。
    CONSTRAINT uk_registrations_member_event UNIQUE (member_id, event_id), -- 同一會員在同一活動只保留一筆報名紀錄。
    CONSTRAINT fk_registrations_member -- 報名會員關聯至 members.id。
        FOREIGN KEY (member_id) REFERENCES members (id) -- member_id 關聯至 members.id。
        ON UPDATE RESTRICT ON DELETE RESTRICT, -- 限制刪除或修改仍被引用的主鍵。
    CONSTRAINT fk_registrations_event -- 報名活動關聯至 events.id。
        FOREIGN KEY (event_id) REFERENCES events (id) -- event_id 關聯至 events.id。
        ON UPDATE RESTRICT ON DELETE RESTRICT, -- 限制刪除或修改仍被引用的主鍵。
    CONSTRAINT chk_registrations_status -- 限制可使用的報名狀態。
        CHECK (status IN ('REGISTERED', 'CANCELLED')),
    CONSTRAINT chk_registrations_cancelled_at CHECK ( -- 取消狀態與取消時間必須一致。
        (status = 'REGISTERED' AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
    ),
    CONSTRAINT chk_registrations_cancelled_time CHECK ( -- 取消時間不得早於報名時間。
        cancelled_at IS NULL OR cancelled_at >= registered_at
    ),

    INDEX idx_registrations_event_status (event_id, status), -- 加速依活動與報名狀態查詢。
    INDEX idx_registrations_member_status (member_id, status) -- 加速依會員與報名狀態查詢。
) ENGINE = InnoDB -- 使用支援交易與外鍵的 InnoDB 儲存引擎。
  DEFAULT CHARACTER SET = utf8mb4 -- 使用支援中文與表情符號的字元集。
  COLLATE = utf8mb4_unicode_ci; -- 使用 Unicode 排序規則，比對時不區分大小寫。
