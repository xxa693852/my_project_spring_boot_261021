-- V6：建立活動行前提醒發送紀錄。
CREATE TABLE event_reminder_deliveries ( -- 活動行前提醒發送紀錄。
    registration_id BIGINT NOT NULL PRIMARY KEY, -- 報名編號兼主鍵（對應 registrations.id；本版本未建立外鍵約束）。
    status VARCHAR(16) NOT NULL, -- 活動行前提醒的發送狀態。
    channel VARCHAR(10) NULL, -- 通知發送管道。
    result_code VARCHAR(40) NULL, -- 通知發送結果代碼。
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) -- 最後更新時間。
        ON UPDATE CURRENT_TIMESTAMP(6) -- 資料變動時自動更新時間。
);
