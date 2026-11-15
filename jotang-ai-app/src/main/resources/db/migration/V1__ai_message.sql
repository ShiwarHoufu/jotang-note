CREATE TABLE ai_message
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    conversation_id CHAR(36)        NOT NULL COMMENT '服务端生成的 UUID，同一会话的消息共用它',
    role            VARCHAR(16)     NOT NULL COMMENT 'USER / ASSISTANT',
    content         TEXT            NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conversation (conversation_id, id),
    KEY idx_user_created (user_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
