-- V4：新增活動封面主題與自訂標籤。
ALTER TABLE events -- 活動資料。
    ADD COLUMN cover_theme VARCHAR(16) NOT NULL DEFAULT 'tavern', -- 活動封面主題代碼。
    ADD COLUMN custom_tags VARCHAR(84) NOT NULL DEFAULT ''; -- 活動自訂標籤。
