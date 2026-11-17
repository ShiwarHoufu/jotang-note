CREATE TABLE college
(
    id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(128)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE `user`
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username      VARCHAR(32)     NOT NULL COMMENT '登录名',
    email         VARCHAR(128)    NOT NULL COMMENT '邮箱，密码找回用',
    password_hash VARCHAR(100)    NOT NULL COMMENT 'BCrypt',
    nickname      VARCHAR(32)     NOT NULL,
    college_id    BIGINT UNSIGNED NOT NULL COMMENT '所属学院，注册时必选（D8）',
    avatar        VARCHAR(255)    NULL COMMENT '头像 Bucket 对象键（非完整 URL；对外拼公网 URL）',
    role          VARCHAR(16)     NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1正常 0禁用（预留）',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
    KEY idx_college (college_id),
    CONSTRAINT fk_user_college FOREIGN KEY (college_id) REFERENCES college (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE password_reset_token
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64)        NOT NULL COMMENT 'SHA-256(原始令牌)，不存明文',
    expires_at DATETIME        NOT NULL COMMENT '签发时间 + 30 分钟',
    used_at    DATETIME        NULL COMMENT '非空 = 已消费，一次性判据',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_token_hash (token_hash),
    KEY idx_user (user_id),
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
