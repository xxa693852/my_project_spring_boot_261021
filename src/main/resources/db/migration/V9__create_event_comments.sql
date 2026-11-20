CREATE TABLE event_comments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    body VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    hidden_reason VARCHAR(200) NULL,
    INDEX idx_event_comments_event (event_id, id),
    CONSTRAINT fk_event_comments_event FOREIGN KEY (event_id) REFERENCES events (id),
    CONSTRAINT fk_event_comments_author FOREIGN KEY (author_id) REFERENCES members (id)
);
