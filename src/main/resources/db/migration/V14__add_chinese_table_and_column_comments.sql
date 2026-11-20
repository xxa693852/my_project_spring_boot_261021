-- 資料表與欄位的繁體中文名稱及用途說明。
-- 使用 MySQL COMMENT 寫入資料庫中繼資料，供資料庫工具讀取。
-- 保留 V1～V13 原始檔案，避免影響 Flyway 已套用版本的校驗碼。
-- MODIFY COLUMN 沿用原有型別、空值、預設值、自動編號與自動更新設定；
-- 原有主鍵、唯一鍵、外鍵、索引及檢查條件均保留，不重新建立。
-- flyway_schema_history 為 Flyway 管理的系統資料表，不在此修改。

-- members（會員資料）
ALTER TABLE members
    COMMENT = '會員資料',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '資料編號（主鍵）',
    MODIFY COLUMN account VARCHAR(50) NOT NULL COMMENT '登入帳號',
    MODIFY COLUMN password_hash VARCHAR(255) NOT NULL COMMENT '登入密碼雜湊值（非明文密碼）',
    MODIFY COLUMN name VARCHAR(50) NOT NULL COMMENT '會員姓名',
    MODIFY COLUMN email VARCHAR(254) NOT NULL COMMENT '電子郵件地址',
    MODIFY COLUMN role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT '會員角色（MEMBER：一般會員；ADMIN：管理員）',
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '會員狀態（ACTIVE：啟用；DISABLED：停用）',
    MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '建立時間',
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最後更新時間',
    MODIFY COLUMN notification_line_user_id VARCHAR(33) NULL COMMENT '綁定通知用的 LINE 使用者識別碼',
    MODIFY COLUMN suspended_at DATETIME(6) NULL COMMENT '停權開始時間',
    MODIFY COLUMN suspended_until DATETIME(6) NULL COMMENT '停權結束時間';

-- events（活動資料）
ALTER TABLE events
    COMMENT = '活動資料',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '資料編號（主鍵）',
    MODIFY COLUMN title VARCHAR(100) NOT NULL COMMENT '活動名稱',
    MODIFY COLUMN description TEXT NOT NULL COMMENT '活動詳細介紹',
    MODIFY COLUMN host_name VARCHAR(50) NOT NULL COMMENT '主持人名稱',
    MODIFY COLUMN location VARCHAR(255) NOT NULL COMMENT '活動地點',
    MODIFY COLUMN start_at DATETIME(6) NOT NULL COMMENT '活動開始時間',
    MODIFY COLUMN end_at DATETIME(6) NOT NULL COMMENT '活動結束時間',
    MODIFY COLUMN registration_deadline DATETIME(6) NOT NULL COMMENT '報名截止時間',
    MODIFY COLUMN capacity INT NOT NULL COMMENT '報名人數上限',
    MODIFY COLUMN beginner_friendly BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否適合新手（0：否；1：是）',
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '活動狀態（OPEN：開放報名；CLOSED：關閉報名；CANCELLED：已取消）',
    MODIFY COLUMN created_by BIGINT NOT NULL COMMENT '建立活動的會員編號（對應 members.id）',
    MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '建立時間',
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最後更新時間',
    MODIFY COLUMN cover_theme VARCHAR(16) NOT NULL DEFAULT 'tavern' COMMENT '活動封面主題代碼',
    MODIFY COLUMN custom_tags VARCHAR(84) NOT NULL DEFAULT '' COMMENT '活動自訂標籤',
    MODIFY COLUMN hidden_from_public_list BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否隱藏於公開活動列表（0：否；1：是）',
    MODIFY COLUMN registration_fee INT NOT NULL DEFAULT 0 COMMENT '活動報名費用（0 表示免費）';

-- registrations（活動報名紀錄）
ALTER TABLE registrations
    COMMENT = '活動報名紀錄',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '資料編號（主鍵）',
    MODIFY COLUMN member_id BIGINT NOT NULL COMMENT '會員編號（對應 members.id）',
    MODIFY COLUMN event_id BIGINT NOT NULL COMMENT '活動編號（對應 events.id）',
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED' COMMENT '報名狀態（REGISTERED：已報名；CANCELLED：已取消；PENDING_PAYMENT：待付款）',
    MODIFY COLUMN registered_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '報名時間',
    MODIFY COLUMN cancelled_at DATETIME(6) NULL COMMENT '取消報名時間',
    MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '建立時間',
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最後更新時間',
    MODIFY COLUMN payment_expires_at DATETIME(6) NULL COMMENT '待付款報名的付款期限';

-- auth_tokens（登入權杖紀錄）
ALTER TABLE auth_tokens
    COMMENT = '登入權杖紀錄',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '資料編號（主鍵）',
    MODIFY COLUMN owner_type VARCHAR(32) NOT NULL COMMENT '權杖持有者類型',
    MODIFY COLUMN owner_id BIGINT NOT NULL COMMENT '權杖持有者編號',
    MODIFY COLUMN access_token_hash VARCHAR(64) NOT NULL COMMENT '存取權杖雜湊值（不儲存原始權杖）',
    MODIFY COLUMN refresh_token_hash VARCHAR(64) NOT NULL COMMENT '更新權杖雜湊值（不儲存原始權杖）',
    MODIFY COLUMN access_expires_at DATETIME(6) NOT NULL COMMENT '存取權杖到期時間',
    MODIFY COLUMN refresh_expires_at DATETIME(6) NOT NULL COMMENT '更新權杖到期時間',
    MODIFY COLUMN revoked TINYINT NOT NULL DEFAULT 0 COMMENT '是否已撤銷（0：否；1：是）',
    MODIFY COLUMN created_at DATETIME(6) NOT NULL COMMENT '建立時間',
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL COMMENT '最後更新時間',
    MODIFY COLUMN deleted_at DATETIME(6) NULL COMMENT '軟刪除時間（空值表示尚未刪除）';

-- password_reset_tokens（密碼重設權杖）
ALTER TABLE password_reset_tokens
    COMMENT = '密碼重設權杖',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '資料編號（主鍵）',
    MODIFY COLUMN member_id BIGINT NOT NULL COMMENT '會員編號（對應 members.id）',
    MODIFY COLUMN token_hash VARCHAR(64) NOT NULL COMMENT '一次性密碼重設權杖的雜湊值',
    MODIFY COLUMN expires_at DATETIME(6) NOT NULL COMMENT '密碼重設權杖到期時間',
    MODIFY COLUMN used TINYINT NOT NULL DEFAULT 0 COMMENT '是否已使用（0：否；1：是）',
    MODIFY COLUMN created_at DATETIME(6) NOT NULL COMMENT '建立時間',
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL COMMENT '最後更新時間',
    MODIFY COLUMN deleted_at DATETIME(6) NULL COMMENT '軟刪除時間（空值表示尚未刪除）';

-- event_cancellation_notifications（活動取消通知紀錄）
ALTER TABLE event_cancellation_notifications
    COMMENT = '活動取消通知紀錄',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '資料編號（主鍵）',
    MODIFY COLUMN event_id BIGINT NOT NULL COMMENT '活動編號（對應 events.id）',
    MODIFY COLUMN member_id BIGINT NOT NULL COMMENT '會員編號（對應 members.id）',
    MODIFY COLUMN event_title VARCHAR(100) NOT NULL COMMENT '通知建立時的活動名稱',
    MODIFY COLUMN event_start_at DATETIME(6) NOT NULL COMMENT '通知建立時的活動開始時間',
    MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '活動取消通知的發送狀態',
    MODIFY COLUMN channel VARCHAR(10) NULL COMMENT '通知發送管道',
    MODIFY COLUMN result_code VARCHAR(40) NULL COMMENT '通知發送結果代碼',
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最後更新時間',
    MODIFY COLUMN payment_note VARCHAR(100) NULL COMMENT '取消活動時的付款或退款說明';

-- event_reminder_deliveries（活動行前提醒發送紀錄）
ALTER TABLE event_reminder_deliveries
    COMMENT = '活動行前提醒發送紀錄',
    MODIFY COLUMN registration_id BIGINT NOT NULL COMMENT '報名編號（主鍵，對應 registrations.id）',
    MODIFY COLUMN status VARCHAR(16) NOT NULL COMMENT '活動行前提醒的發送狀態',
    MODIFY COLUMN channel VARCHAR(10) NULL COMMENT '通知發送管道',
    MODIFY COLUMN result_code VARCHAR(40) NULL COMMENT '通知發送結果代碼',
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最後更新時間';

-- member_suspension_actions（會員停權操作紀錄）
ALTER TABLE member_suspension_actions
    COMMENT = '會員停權操作紀錄',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '資料編號（主鍵）',
    MODIFY COLUMN member_id BIGINT NOT NULL COMMENT '會員編號（對應 members.id）',
    MODIFY COLUMN actor_id BIGINT NOT NULL COMMENT '執行操作的管理員編號（對應 members.id）',
    MODIFY COLUMN action VARCHAR(16) NOT NULL COMMENT '停權或解除停權的操作類型',
    MODIFY COLUMN reason VARCHAR(500) NOT NULL COMMENT '執行停權或解除停權的原因',
    MODIFY COLUMN acted_at DATETIME(6) NOT NULL COMMENT '操作執行時間',
    MODIFY COLUMN suspended_until DATETIME(6) NULL COMMENT '停權結束時間',
    MODIFY COLUMN notification_status VARCHAR(16) NULL COMMENT '停權操作通知的發送狀態',
    MODIFY COLUMN notification_channel VARCHAR(10) NULL COMMENT '停權操作通知的發送管道',
    MODIFY COLUMN notification_result_code VARCHAR(40) NULL COMMENT '停權操作通知的發送結果代碼';

-- event_comments（活動留言）
ALTER TABLE event_comments
    COMMENT = '活動留言',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '資料編號（主鍵）',
    MODIFY COLUMN event_id BIGINT NOT NULL COMMENT '活動編號（對應 events.id）',
    MODIFY COLUMN author_id BIGINT NOT NULL COMMENT '留言作者的會員編號（對應 members.id）',
    MODIFY COLUMN body VARCHAR(500) NOT NULL COMMENT '留言內容',
    MODIFY COLUMN created_at DATETIME(6) NOT NULL COMMENT '建立時間',
    MODIFY COLUMN hidden_reason VARCHAR(200) NULL COMMENT '留言被隱藏的原因';

-- comment_moderation_notifications（留言管理通知紀錄）
ALTER TABLE comment_moderation_notifications
    COMMENT = '留言管理通知紀錄',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '資料編號（主鍵）',
    MODIFY COLUMN member_id BIGINT NOT NULL COMMENT '會員編號（對應 members.id）',
    MODIFY COLUMN comment_id BIGINT NOT NULL COMMENT '被管理的留言編號（對應 event_comments.id）',
    MODIFY COLUMN event_title VARCHAR(100) NOT NULL COMMENT '通知建立時的活動名稱',
    MODIFY COLUMN comment_created_at DATETIME(6) NOT NULL COMMENT '被管理留言的原始建立時間',
    MODIFY COLUMN reason VARCHAR(200) NULL COMMENT '留言管理原因',
    MODIFY COLUMN status VARCHAR(16) NOT NULL COMMENT '留言管理通知的發送狀態',
    MODIFY COLUMN channel VARCHAR(10) NULL COMMENT '通知發送管道',
    MODIFY COLUMN result_code VARCHAR(40) NULL COMMENT '通知發送結果代碼';

-- payments（付款交易紀錄）
ALTER TABLE payments
    COMMENT = '付款交易紀錄',
    MODIFY COLUMN id BIGINT AUTO_INCREMENT COMMENT '資料編號（主鍵）',
    MODIFY COLUMN registration_id BIGINT NOT NULL COMMENT '報名編號（對應 registrations.id）',
    MODIFY COLUMN merchant_order_no VARCHAR(64) NOT NULL COMMENT '商店訂單編號（每次付款嘗試皆唯一）',
    MODIFY COLUMN merchant_id VARCHAR(32) NOT NULL COMMENT '金流商店代號',
    MODIFY COLUMN provider VARCHAR(32) NOT NULL COMMENT '金流服務供應商',
    MODIFY COLUMN provider_trade_no VARCHAR(64) NULL COMMENT '金流供應商的交易編號',
    MODIFY COLUMN payment_method VARCHAR(32) NULL COMMENT '付款方式',
    MODIFY COLUMN amount INT NOT NULL COMMENT '本次交易金額',
    MODIFY COLUMN payment_status VARCHAR(32) NOT NULL COMMENT '付款交易狀態',
    MODIFY COLUMN trade_sha VARCHAR(128) NULL COMMENT '金流交易驗證雜湊值',
    MODIFY COLUMN return_code VARCHAR(32) NULL COMMENT '金流回傳結果代碼',
    MODIFY COLUMN return_message VARCHAR(255) NULL COMMENT '金流回傳結果訊息',
    MODIFY COLUMN paid_at DATETIME(6) NULL COMMENT '付款完成時間',
    MODIFY COLUMN refunded_at DATETIME(6) NULL COMMENT '退款完成時間',
    MODIFY COLUMN refund_note VARCHAR(255) NULL COMMENT '退款備註',
    MODIFY COLUMN created_at DATETIME(6) NOT NULL COMMENT '建立時間',
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL COMMENT '最後更新時間';
