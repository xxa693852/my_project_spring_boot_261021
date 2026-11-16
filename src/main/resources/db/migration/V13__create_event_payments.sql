-- 待付款期限到達時，查詢會立即釋放名額，即使尚未有背景工作更新資料。
ALTER TABLE registrations
    DROP CHECK chk_registrations_status,
    DROP CHECK chk_registrations_cancelled_at,
    ADD COLUMN payment_expires_at DATETIME(6) NULL,
    ADD CONSTRAINT chk_registrations_status CHECK (status IN ('REGISTERED', 'CANCELLED', 'PENDING_PAYMENT')),
    ADD CONSTRAINT chk_registrations_cancelled_at CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    ADD CONSTRAINT chk_registrations_pending_expiry CHECK (status <> 'PENDING_PAYMENT' OR payment_expires_at IS NOT NULL);

-- 同一筆報名可以保留多次付款嘗試，每次使用各自唯一的商店訂單編號。
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    registration_id BIGINT NOT NULL,
    merchant_order_no VARCHAR(64) NOT NULL UNIQUE,
    merchant_id VARCHAR(32) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_trade_no VARCHAR(64) NULL,
    payment_method VARCHAR(32) NULL,
    amount INT NOT NULL CHECK (amount > 0),
    payment_status VARCHAR(32) NOT NULL,
    trade_sha VARCHAR(128) NULL,
    return_code VARCHAR(32) NULL,
    return_message VARCHAR(255) NULL,
    paid_at DATETIME(6) NULL,
    refunded_at DATETIME(6) NULL,
    refund_note VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    FOREIGN KEY (registration_id) REFERENCES registrations(id) ON DELETE CASCADE,
    INDEX idx_payments_registration (registration_id, id)
);

ALTER TABLE event_cancellation_notifications ADD COLUMN payment_note VARCHAR(100) NULL;
