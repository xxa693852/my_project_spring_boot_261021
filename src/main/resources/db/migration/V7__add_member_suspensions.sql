ALTER TABLE members
    ADD COLUMN suspended_at DATETIME(6) NULL,
    ADD COLUMN suspended_until DATETIME(6) NULL;

CREATE TABLE member_suspension_actions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    acted_at DATETIME(6) NOT NULL,
    suspended_until DATETIME(6) NULL,
    INDEX idx_member_suspension_actions (member_id, id),
    FOREIGN KEY (member_id) REFERENCES members(id),
    FOREIGN KEY (actor_id) REFERENCES members(id)
);
