-- Jotang Note MVP 建表脚本，对应《概要设计》§3.2
-- 全部 IF NOT EXISTS，可重复执行

-- 学院（预置数据，用户注册时必选，D8）
CREATE TABLE IF NOT EXISTS `college` (
  `id`   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(128)    NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户
CREATE TABLE IF NOT EXISTS `user` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username`      VARCHAR(32)     NOT NULL COMMENT '登录名',
  `email`         VARCHAR(128)    NOT NULL COMMENT '邮箱，密码找回用',
  `password_hash` VARCHAR(100)    NOT NULL COMMENT 'BCrypt',
  `nickname`      VARCHAR(32)     NOT NULL,
  `college_id`    BIGINT UNSIGNED NOT NULL COMMENT '所属学院，注册时必选（D8）',
  `avatar`        VARCHAR(255)    NULL     COMMENT '头像 Bucket 对象键（非完整 URL；对外拼公网 URL）',
  `role`          VARCHAR(16)     NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
  `status`        TINYINT         NOT NULL DEFAULT 1      COMMENT '1正常 0禁用（预留）',
  `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  KEY `idx_college` (`college_id`),
  CONSTRAINT `fk_user_college` FOREIGN KEY (`college_id`) REFERENCES `college`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 课程（预置数据，含一条固定的「其他」）
-- D8：不再记开课学院——学院挂在上传者身上，笔记的学院由 user.college_id 推导（概要设计 §3.3）
CREATE TABLE IF NOT EXISTS `course` (
  `id`       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`     VARCHAR(128)    NOT NULL,
  `is_other` TINYINT         NOT NULL DEFAULT 0 COMMENT '1=内置「其他」',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 笔记（软删除：status 含 DELETED，行不物理删除）
CREATE TABLE IF NOT EXISTS `note` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `uploader_id`    BIGINT UNSIGNED NOT NULL,
  `course_id`      BIGINT UNSIGNED NOT NULL,
  `title`          VARCHAR(128)    NOT NULL,
  `summary`        VARCHAR(512)    NULL COMMENT '简介，存原文',
  `teacher`        VARCHAR(64)     NULL,
  `status`         VARCHAR(16)     NOT NULL DEFAULT 'ONLINE' COMMENT 'ONLINE / OFFLINE / DELETED',
  `view_count`     INT UNSIGNED    NOT NULL DEFAULT 0,
  `download_count` INT UNSIGNED    NOT NULL DEFAULT 0,
  `favorite_count` INT UNSIGNED    NOT NULL DEFAULT 0,
  `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at`     DATETIME        NULL COMMENT 'status=DELETED 时写入',
  PRIMARY KEY (`id`),
  KEY `idx_status_created`  (`status`, `created_at`),      -- 首页最新
  KEY `idx_status_view`     (`status`, `view_count`),      -- 热门·浏览
  KEY `idx_status_download` (`status`, `download_count`),  -- 热门·下载
  KEY `idx_course_status`   (`course_id`, `status`),       -- 课程页
  KEY `idx_uploader_status` (`uploader_id`, `status`),     -- 我的上传
  CONSTRAINT `fk_note_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `user`(`id`),
  CONSTRAINT `fk_note_course`   FOREIGN KEY (`course_id`)   REFERENCES `course`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 笔记文件（一份笔记一个文件，独立成表为 V2 多附件留余地）
CREATE TABLE IF NOT EXISTS `note_file` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `note_id`       BIGINT UNSIGNED NOT NULL,
  `storage_key`   VARCHAR(255)    NOT NULL COMMENT 'OSS 对象键：notes/yyyy/MM/{uuid}.{ext}',
  `original_name` VARCHAR(255)    NOT NULL COMMENT '原始文件名，下载时还原',
  `size`          BIGINT UNSIGNED NOT NULL,
  `content_type`  VARCHAR(128)    NOT NULL COMMENT '服务端判定的类型',
  `is_purged`     TINYINT         NOT NULL DEFAULT 0 COMMENT '1=对象已物理清理',
  `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_note_id`     (`note_id`),
  UNIQUE KEY `uk_storage_key` (`storage_key`),
  CONSTRAINT `fk_file_note` FOREIGN KEY (`note_id`) REFERENCES `note`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 标签
CREATE TABLE IF NOT EXISTS `tag` (
  `id`   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(32)     NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 笔记-标签 关联表（对应 NOTE }o--o{ TAG）
CREATE TABLE IF NOT EXISTS `note_tag` (
  `note_id` BIGINT UNSIGNED NOT NULL,
  `tag_id`  BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (`note_id`, `tag_id`),
  KEY `idx_tag` (`tag_id`),
  CONSTRAINT `fk_nt_note` FOREIGN KEY (`note_id`) REFERENCES `note`(`id`),
  CONSTRAINT `fk_nt_tag`  FOREIGN KEY (`tag_id`)  REFERENCES `tag`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 收藏（note_id 外键指向软删除的 note，永不悬空）
CREATE TABLE IF NOT EXISTS `favorite` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `note_id`    BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_note`   (`user_id`, `note_id`),
  KEY `idx_user_created` (`user_id`, `created_at`),
  KEY `idx_note`         (`note_id`),
  CONSTRAINT `fk_fav_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`),
  CONSTRAINT `fk_fav_note` FOREIGN KEY (`note_id`) REFERENCES `note`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 密码找回令牌（D6：落库、一次性、可重启）
CREATE TABLE IF NOT EXISTS `password_reset_token` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `token_hash` CHAR(64)        NOT NULL COMMENT 'SHA-256(原始令牌)，不存明文',
  `expires_at` DATETIME        NOT NULL COMMENT '签发时间 + 30 分钟',
  `used_at`    DATETIME        NULL     COMMENT '非空 = 已消费，一次性判据',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token_hash` (`token_hash`),
  KEY `idx_user` (`user_id`),
  CONSTRAINT `fk_prt_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
