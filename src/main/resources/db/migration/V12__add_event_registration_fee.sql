-- V12：新增活動報名費用，既有活動預設免費。
-- 既有活動與省略費用的資料預設為免費。
ALTER TABLE events -- 活動資料。
    ADD COLUMN registration_fee INT NOT NULL DEFAULT 0, -- 活動報名費用（0 表示免費）。
    ADD CONSTRAINT chk_events_registration_fee CHECK (registration_fee >= 0); -- 活動報名費不得小於零。
