-- V13：新增待付款報名、付款期限與交易紀錄。
-- 待付款期限到達時，查詢會立即釋放名額，即使尚未有背景工作更新資料。
ALTER TABLE registrations -- 活動報名紀錄。
    DROP CHECK chk_registrations_status, -- 移除舊條件，於下方重新定義：限制可使用的報名狀態。
    DROP CHECK chk_registrations_cancelled_at, -- 移除舊條件，於下方重新定義：取消狀態與取消時間必須一致。
    ADD COLUMN payment_expires_at DATETIME(6) NULL, -- 待付款報名的付款期限。
    ADD CONSTRAINT chk_registrations_status CHECK (status IN ('REGISTERED', 'CANCELLED', 'PENDING_PAYMENT')), -- 限制可使用的報名狀態。
    ADD CONSTRAINT chk_registrations_cancelled_at CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)), -- 取消狀態與取消時間必須一致。
    ADD CONSTRAINT chk_registrations_pending_expiry CHECK (status <> 'PENDING_PAYMENT' OR payment_expires_at IS NOT NULL); -- 待付款報名必須有付款期限。

-- 同一筆報名可以保留多次付款嘗試，每次使用各自唯一的商店訂單編號。
CREATE TABLE payments ( -- 付款交易紀錄。
    id BIGINT AUTO_INCREMENT PRIMARY KEY, -- 資料編號（主鍵）。
    registration_id BIGINT NOT NULL, -- 報名編號（對應 registrations.id）。
    merchant_order_no VARCHAR(64) NOT NULL UNIQUE, -- 商店訂單編號（每次付款嘗試皆唯一）。
    merchant_id VARCHAR(32) NOT NULL, -- 金流商店代號。
    provider VARCHAR(32) NOT NULL, -- 金流服務供應商。
    provider_trade_no VARCHAR(64) NULL, -- 金流供應商的交易編號。
    payment_method VARCHAR(32) NULL, -- 付款方式。
    amount INT NOT NULL CHECK (amount > 0), -- 本次交易金額。
    payment_status VARCHAR(32) NOT NULL, -- 付款交易狀態。
    trade_sha VARCHAR(128) NULL, -- 金流交易驗證雜湊值。
    return_code VARCHAR(32) NULL, -- 金流回傳結果代碼。
    return_message VARCHAR(255) NULL, -- 金流回傳結果訊息。
    paid_at DATETIME(6) NULL, -- 付款完成時間。
    refunded_at DATETIME(6) NULL, -- 退款完成時間。
    refund_note VARCHAR(255) NULL, -- 退款備註。
    created_at DATETIME(6) NOT NULL, -- 建立時間。
    updated_at DATETIME(6) NOT NULL, -- 最後更新時間。
    FOREIGN KEY (registration_id) REFERENCES registrations(id) ON DELETE CASCADE, -- registration_id 關聯至 registrations.id；刪除報名時一併刪除付款紀錄。
    INDEX idx_payments_registration (registration_id, id) -- 加速查詢報名的各次付款紀錄。
);

ALTER TABLE event_cancellation_notifications ADD COLUMN payment_note VARCHAR(100) NULL; -- 活動取消通知紀錄；取消活動時的付款或退款說明。
