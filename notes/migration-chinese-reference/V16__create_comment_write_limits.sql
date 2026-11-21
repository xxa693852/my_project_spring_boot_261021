-- 每位會員跨活動共用；不連動留言刪除。
CREATE TABLE comment_write_limits ( -- comment_write_limits（留言撰寫頻率限制）。
    member_id BIGINT NOT NULL PRIMARY KEY, -- member_id（會員編號）：主鍵，每位會員一筆紀錄。
    window_start BIGINT NOT NULL, -- window_start（計數時間區間的起點）。
    attempts INT NOT NULL, -- attempts（目前區間內的嘗試次數）。
    last_attempt BIGINT NOT NULL, -- last_attempt（最近一次嘗試時間）。
    last_write BIGINT NULL -- last_write（最近一次成功寫入留言的時間）：尚無紀錄時可為空值。
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4; -- 使用支援交易的 InnoDB 引擎及支援中文的 utf8mb4 字元集。
