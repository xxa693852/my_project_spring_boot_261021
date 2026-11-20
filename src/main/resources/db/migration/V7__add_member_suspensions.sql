-- V7：新增會員停權時間與停權操作紀錄。
ALTER TABLE members -- 會員資料。
    ADD COLUMN suspended_at DATETIME(6) NULL, -- 停權開始時間。
    ADD COLUMN suspended_until DATETIME(6) NULL; -- 停權結束時間。

CREATE TABLE member_suspension_actions ( -- 會員停權操作紀錄。
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, -- 資料編號（主鍵）。
    member_id BIGINT NOT NULL, -- 會員編號（對應 members.id）。
    actor_id BIGINT NOT NULL, -- 操作管理員的會員編號（對應 members.id；V15 改名為 actor_member_id）。
    action VARCHAR(16) NOT NULL, -- 停權或解除停權的操作類型。
    reason VARCHAR(500) NOT NULL, -- 執行停權或解除停權的原因。
    acted_at DATETIME(6) NOT NULL, -- 操作執行時間。
    suspended_until DATETIME(6) NULL, -- 停權結束時間。
    INDEX idx_member_suspension_actions (member_id, id), -- 加速查詢會員停權操作紀錄。
    FOREIGN KEY (member_id) REFERENCES members(id), -- member_id 關聯至 members.id。
    FOREIGN KEY (actor_id) REFERENCES members(id) -- actor_id 關聯至 members.id。
);
