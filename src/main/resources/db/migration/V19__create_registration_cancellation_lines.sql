-- 個別取消增加 LINE 通知，V18 Email 紀錄維持不變；不回填舊取消紀錄。
CREATE TABLE registration_cancellation_lines (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT 'LINE通知編號',
    cancellation_id BIGINT NOT NULL COMMENT '對應管理員取消紀錄編號',
    recipient VARCHAR(33) NULL COMMENT '取消當時綁定的LINE使用者識別碼快照',
    body TEXT NOT NULL COMMENT '對外通知內容快照，與Email相同',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待寄、SENDING處理中、SENT服務接受、NOT_BOUND未綁定、NOT_CONFIGURED服務未設定、RECIPIENT_ERROR收件人確認失敗、REJECTED推播遭拒、UNKNOWN結果待確認',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最後處理時間',
    UNIQUE KEY uq_registration_cancellation_line (cancellation_id), -- 每次取消僅建立一筆LINE通知
    INDEX idx_registration_cancellation_line_queue (status, id), -- 排程領取待推播紀錄
    CONSTRAINT fk_registration_cancellation_line FOREIGN KEY (cancellation_id) REFERENCES registration_cancellations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理員個別取消報名的LINE通知';
