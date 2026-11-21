-- 統一會員關聯欄位命名：主鍵維持 members.id，外鍵使用 member_id。
-- 一張表有兩個不同角色的會員時，以角色前綴區分，例如 actor_member_id。
-- 只重新命名欄位及其外鍵、索引；保留資料、中文註解與原有刪除／更新規則。
-- V1～V14 為已套用版本，不回頭修改，避免 Flyway 校驗碼不一致。

-- 活動建立者：events.member_id -> members.id。
ALTER TABLE events -- events（活動資料）：調整活動建立者的會員關聯名稱。
    DROP FOREIGN KEY fk_events_created_by, -- 移除活動建立者的舊外鍵，稍後以新名稱建立。
    RENAME COLUMN created_by TO member_id, -- created_by（活動建立者編號）改名為 member_id（會員編號）。
    RENAME INDEX idx_events_created_by TO idx_events_member_id, -- 將活動建立者索引改為會員編號索引名稱。
    ADD CONSTRAINT fk_events_member -- 建立活動與會員之間的新外鍵名稱。
        FOREIGN KEY (member_id) REFERENCES members (id) -- member_id（會員編號）參照 members.id（會員資料的主鍵）。
        ON UPDATE RESTRICT ON DELETE RESTRICT; -- 有關聯資料時，限制更新或刪除被參照的會員主鍵。

-- 留言作者：event_comments.member_id -> members.id。
ALTER TABLE event_comments -- event_comments（活動留言）：調整留言作者的會員關聯名稱。
    DROP FOREIGN KEY fk_event_comments_author, -- 移除留言作者的舊外鍵。
    RENAME COLUMN author_id TO member_id, -- author_id（留言作者編號）改名為 member_id（會員編號）。
    RENAME INDEX fk_event_comments_author TO idx_event_comments_member_id, -- 將留言作者索引改為會員編號索引名稱。
    ADD CONSTRAINT fk_event_comments_member -- 建立留言與會員之間的新外鍵名稱。
        FOREIGN KEY (member_id) REFERENCES members (id) -- member_id（會員編號）參照 members.id（會員資料的主鍵）。
        ON UPDATE RESTRICT ON DELETE RESTRICT; -- 有關聯資料時，限制更新或刪除被參照的會員主鍵。

-- 被停權會員仍為 member_id；執行操作的管理員改為 actor_member_id。
ALTER TABLE member_suspension_actions -- member_suspension_actions（會員停權操作紀錄）：調整執行者的會員關聯名稱。
    DROP FOREIGN KEY member_suspension_actions_ibfk_2, -- 移除操作執行者原本由 MySQL 自動命名的外鍵。
    RENAME COLUMN actor_id TO actor_member_id, -- actor_id（操作執行者編號）改名為 actor_member_id（執行操作的會員編號）。
    RENAME INDEX actor_id TO idx_member_suspension_actions_actor_member_id, -- 將操作執行者索引改為明確的會員關聯索引名稱。
    ADD CONSTRAINT fk_member_suspension_actions_actor_member -- 建立操作執行者與會員之間的新外鍵名稱。
        FOREIGN KEY (actor_member_id) REFERENCES members (id) -- actor_member_id（執行操作的會員編號）參照 members.id。
        ON UPDATE RESTRICT ON DELETE RESTRICT; -- 有關聯資料時，限制更新或刪除被參照的會員主鍵。
