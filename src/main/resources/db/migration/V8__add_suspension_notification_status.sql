ALTER TABLE member_suspension_actions
    ADD COLUMN notification_status VARCHAR(16) NULL,
    ADD COLUMN notification_channel VARCHAR(10) NULL,
    ADD COLUMN notification_result_code VARCHAR(40) NULL,
    ADD INDEX idx_suspension_notifications (notification_status, id);
