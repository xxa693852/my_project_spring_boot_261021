-- V11：新增活動公開列表隱藏設定。
ALTER TABLE events ADD COLUMN hidden_from_public_list BOOLEAN NOT NULL DEFAULT FALSE; -- 活動資料；是否隱藏於公開活動列表（0：否；1：是）。
