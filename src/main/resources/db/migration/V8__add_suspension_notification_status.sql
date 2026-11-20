-- V8：新增停權通知發送狀態、管道與結果。
ALTER TABLE member_suspension_actions -- 會員停權操作紀錄。
    ADD COLUMN notification_status VARCHAR(16) NULL, -- 停權操作通知的發送狀態。
    ADD COLUMN notification_channel VARCHAR(10) NULL, -- 停權操作通知的發送管道。
    ADD COLUMN notification_result_code VARCHAR(40) NULL, -- 停權操作通知的發送結果代碼。
    ADD INDEX idx_suspension_notifications (notification_status, id); -- 加速讀取待發送的停權通知。
