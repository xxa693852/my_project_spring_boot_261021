-- 統一會員關聯欄位命名：主鍵維持 members.id，外鍵使用 member_id。
-- 一張表有兩個不同角色的會員時，以角色前綴區分，例如 actor_member_id。
-- 只重新命名欄位及其外鍵、索引；保留資料、中文註解與原有刪除／更新規則。
-- V1～V14 為已套用版本，不回頭修改，避免 Flyway 校驗碼不一致。

-- 活動建立者：events.member_id -> members.id。
ALTER TABLE events
    DROP FOREIGN KEY fk_events_created_by,
    RENAME COLUMN created_by TO member_id,
    RENAME INDEX idx_events_created_by TO idx_events_member_id,
    ADD CONSTRAINT fk_events_member
        FOREIGN KEY (member_id) REFERENCES members (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

-- 留言作者：event_comments.member_id -> members.id。
ALTER TABLE event_comments
    DROP FOREIGN KEY fk_event_comments_author,
    RENAME COLUMN author_id TO member_id,
    RENAME INDEX fk_event_comments_author TO idx_event_comments_member_id,
    ADD CONSTRAINT fk_event_comments_member
        FOREIGN KEY (member_id) REFERENCES members (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

-- 被停權會員仍為 member_id；執行操作的管理員改為 actor_member_id。
ALTER TABLE member_suspension_actions
    DROP FOREIGN KEY member_suspension_actions_ibfk_2,
    RENAME COLUMN actor_id TO actor_member_id,
    RENAME INDEX actor_id TO idx_member_suspension_actions_actor_member_id,
    ADD CONSTRAINT fk_member_suspension_actions_actor_member
        FOREIGN KEY (actor_member_id) REFERENCES members (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;
