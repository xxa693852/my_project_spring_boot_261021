CREATE TABLE comment_moderation_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    comment_id BIGINT NOT NULL,
    event_title VARCHAR(100) NOT NULL,
    comment_created_at DATETIME(6) NOT NULL,
    reason VARCHAR(200) NULL,
    status VARCHAR(16) NOT NULL,
    channel VARCHAR(10) NULL,
    result_code VARCHAR(40) NULL,
    INDEX idx_comment_moderation_pending (status, id)
);
