-- 每位會員跨活動共用；不連動留言刪除。
CREATE TABLE comment_write_limits (member_id BIGINT NOT NULL PRIMARY KEY, window_start BIGINT NOT NULL, attempts INT NOT NULL, last_attempt BIGINT NOT NULL, last_write BIGINT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
