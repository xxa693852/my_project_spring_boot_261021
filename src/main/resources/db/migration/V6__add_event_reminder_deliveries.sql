CREATE TABLE event_reminder_deliveries (
    registration_id BIGINT NOT NULL PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    channel VARCHAR(10) NULL,
    result_code VARCHAR(40) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6)
);
