-- 一次性迁移：把过渡期单体库里的用户数据复制到 user-service 的独立库。
--
-- 前置：
--   1. 先启动过一次 user-service，让 Flyway 建好 jotang_user 的表并写入学院种子；
--   2. 两个库在同一个 MySQL 实例上，当前账号能跨库读写。
--
-- 说明：
--   - 保留原 id：JWT 的 sub、note.uploader_id、password_reset_token.user_id 都按 id 引用；
--   - INSERT IGNORE：学院种子已经占用的 id 直接跳过，其余数据按原 id 复制；
--   - 只复制 user / college / password_reset_token，note 相关表仍留在单体库。

INSERT IGNORE INTO jotang_user.college (id, name)
SELECT id, name FROM jotang_note.college;

INSERT IGNORE INTO jotang_user.`user`
SELECT * FROM jotang_note.`user`;

INSERT IGNORE INTO jotang_user.password_reset_token
SELECT * FROM jotang_note.password_reset_token;
