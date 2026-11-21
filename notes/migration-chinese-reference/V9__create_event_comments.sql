-- V9：建立活動留言與其外鍵關聯。
CREATE TABLE event_comments ( -- 活動留言。
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, -- 資料編號（主鍵）。
    event_id BIGINT NOT NULL, -- 活動編號（對應 events.id）。
    author_id BIGINT NOT NULL, -- 留言作者的會員編號（對應 members.id；V15 改名為 member_id）。
    body VARCHAR(500) NOT NULL, -- 留言內容。
    created_at DATETIME(6) NOT NULL, -- 建立時間。
    hidden_reason VARCHAR(200) NULL, -- 留言被隱藏的原因。
    INDEX idx_event_comments_event (event_id, id), -- 加速依活動與留言編號查詢。
    CONSTRAINT fk_event_comments_event FOREIGN KEY (event_id) REFERENCES events (id), -- 留言所屬活動關聯至 events.id。
    CONSTRAINT fk_event_comments_author FOREIGN KEY (author_id) REFERENCES members (id) -- 留言作者關聯至 members.id。
);
