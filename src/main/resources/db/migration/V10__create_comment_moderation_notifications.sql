-- V10：建立留言管理通知紀錄。
CREATE TABLE comment_moderation_notifications ( -- 留言管理通知紀錄。
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, -- 資料編號（主鍵）。
    member_id BIGINT NOT NULL, -- 會員編號（對應 members.id）。
    comment_id BIGINT NOT NULL, -- 被管理的留言編號（對應 event_comments.id）。
    event_title VARCHAR(100) NOT NULL, -- 通知建立時的活動名稱。
    comment_created_at DATETIME(6) NOT NULL, -- 被管理留言的原始建立時間。
    reason VARCHAR(200) NULL, -- 留言管理原因。
    status VARCHAR(16) NOT NULL, -- 留言管理通知的發送狀態。
    channel VARCHAR(10) NULL, -- 通知發送管道。
    result_code VARCHAR(40) NULL, -- 通知發送結果代碼。
    INDEX idx_comment_moderation_pending (status, id) -- 加速讀取待發送的留言管理通知。
);
