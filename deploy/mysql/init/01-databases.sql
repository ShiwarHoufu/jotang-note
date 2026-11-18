-- 仅在 MySQL 数据卷为空（首次启动）时执行。
-- 三个服务各自一个 schema，应用账号沿用 root，故这里只建库。
-- 排序规则与原生 MySQL 8.0 的默认值一致，避免恢复旧数据时出现混用。
CREATE DATABASE IF NOT EXISTS jotang_note
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS jotang_user
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS jotang_ai
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
