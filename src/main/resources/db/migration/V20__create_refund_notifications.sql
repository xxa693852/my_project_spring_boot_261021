-- 退款登記成功後的雙管道通知；不補發歷史退款。
CREATE TABLE refund_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '退款通知編號',
    payment_id BIGINT NOT NULL COMMENT '對應付款訂單編號',
    channel VARCHAR(10) NOT NULL COMMENT '通知管道EMAIL或LINE',
    recipient VARCHAR(254) NULL COMMENT '登記當時的Email或LINE收件人快照',
    body TEXT NOT NULL COMMENT '對外退款處理通知內容快照，不含內部說明',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待寄、SENDING處理中、SENT服務接受、NOT_BOUND未綁定、NOT_CONFIGURED未設定、FAILED缺收件人、UNKNOWN結果待確認',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '通知建立時間',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '通知最後更新時間',
    UNIQUE KEY uq_refund_notification (payment_id, channel), -- 每筆訂單每管道僅一份退款通知
    INDEX idx_refund_notification_queue (status, id), -- 排程領取待寄通知
    CONSTRAINT fk_refund_notification_payment FOREIGN KEY (payment_id) REFERENCES payments(id) -- 保留付款對應
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全額退款登記的通知佇列';
