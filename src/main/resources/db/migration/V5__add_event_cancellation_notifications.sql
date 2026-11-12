ALTER TABLE members
    ADD COLUMN notification_line_user_id VARCHAR(33) NULL,
    ADD CONSTRAINT uq_member_notification_line_user UNIQUE (notification_line_user_id);

CREATE TABLE event_cancellation_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    event_title VARCHAR(100) NOT NULL,
    event_start_at DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    channel VARCHAR(10) NULL,
    result_code VARCHAR(40) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_event_cancellation_recipient UNIQUE (event_id, member_id),
    INDEX idx_cancellation_delivery_queue (status, id)
);
