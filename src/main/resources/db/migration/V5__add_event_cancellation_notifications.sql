-- V5：新增 LINE 通知綁定與活動取消通知紀錄。
ALTER TABLE members -- 會員資料。
    ADD COLUMN notification_line_user_id VARCHAR(33) NULL, -- 綁定通知用的 LINE 使用者識別碼。
    ADD CONSTRAINT uq_member_notification_line_user UNIQUE (notification_line_user_id); -- 同一 LINE 使用者只能綁定一位會員。

CREATE TABLE event_cancellation_notifications ( -- 活動取消通知紀錄。
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, -- 資料編號（主鍵）。
    event_id BIGINT NOT NULL, -- 活動編號（對應 events.id）。
    member_id BIGINT NOT NULL, -- 會員編號（對應 members.id）。
    event_title VARCHAR(100) NOT NULL, -- 通知建立時的活動名稱。
    event_start_at DATETIME(6) NOT NULL, -- 通知建立時的活動開始時間。
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- 活動取消通知的發送狀態。
    channel VARCHAR(10) NULL, -- 通知發送管道。
    result_code VARCHAR(40) NULL, -- 通知發送結果代碼。
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), -- 最後更新時間。
    CONSTRAINT uq_event_cancellation_recipient UNIQUE (event_id, member_id), -- 同一活動對同一會員只建立一筆取消通知。
    INDEX idx_cancellation_delivery_queue (status, id) -- 加速讀取待處理的活動取消通知。
);
