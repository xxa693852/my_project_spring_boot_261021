-- 既有活動與省略費用的資料預設為免費。
ALTER TABLE events
    ADD COLUMN registration_fee INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_events_registration_fee CHECK (registration_fee >= 0);
