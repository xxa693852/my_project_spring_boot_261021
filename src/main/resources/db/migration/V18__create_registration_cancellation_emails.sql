-- 個別取消的 Email 寄送佇列；與取消報名共用資料庫交易。
CREATE TABLE registration_cancellation_emails (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '通知編號',
    cancellation_id BIGINT NOT NULL COMMENT '管理員取消紀錄編號',
    recipient VARCHAR(254) NULL COMMENT '玩家收件信箱快照',
    body TEXT NOT NULL COMMENT '寄送內容快照，包含對外取消原因',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待寄、SENDING處理中、SENT郵件服務接受、FAILED無信箱、UNKNOWN結果待確認',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最後處理時間',
    UNIQUE KEY uq_registration_cancellation_email (cancellation_id), -- 每次取消只排入一封通知
    INDEX idx_registration_cancellation_email_queue (status, id), -- 排程取待寄通知
    CONSTRAINT fk_registration_cancellation_email FOREIGN KEY (cancellation_id) REFERENCES registration_cancellations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理員個別取消報名的電子郵件通知';
