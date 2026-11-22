-- 保存管理員個別取消的歷程，不覆寫既有報名與付款資料。
CREATE TABLE registration_cancellations (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '取消紀錄編號（主鍵）',
    registration_id BIGINT NOT NULL COMMENT '對應報名編號',
    event_id BIGINT NOT NULL COMMENT '活動編號',
    member_id BIGINT NOT NULL COMMENT '被取消報名的會員編號',
    operator_id BIGINT NOT NULL COMMENT '執行取消的管理員編號',
    reason VARCHAR(255) NOT NULL COMMENT '管理員取消原因',
    previous_status VARCHAR(20) NOT NULL COMMENT '取消前的報名狀態',
    payment_status VARCHAR(32) NULL COMMENT '取消前最新訂單的付款狀態；無訂單為空值',
    registered_at DATETIME(6) NULL COMMENT '此次報名時間快照',
    cancelled_at DATETIME(6) NOT NULL COMMENT '管理員取消時間（台北時間）',
    PRIMARY KEY (id), -- 每次取消都新增一筆
    INDEX idx_registration_cancellations_event (event_id, cancelled_at, id), -- 活動取消歷程查詢
    CONSTRAINT fk_registration_cancellations_registration FOREIGN KEY (registration_id) REFERENCES registrations(id),
    CONSTRAINT fk_registration_cancellations_event FOREIGN KEY (event_id) REFERENCES events(id),
    CONSTRAINT fk_registration_cancellations_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_registration_cancellations_operator FOREIGN KEY (operator_id) REFERENCES members(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理員個別取消報名歷程';
