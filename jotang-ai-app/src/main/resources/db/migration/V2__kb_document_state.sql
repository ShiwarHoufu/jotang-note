CREATE TABLE kb_document_state
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    document_path   VARCHAR(512)    NOT NULL COMMENT '知识库目录下的相对路径',
    document_id     CHAR(64)        NOT NULL COMMENT 'sha256(document_path)，与向量 payload 对应',
    content_hash    CHAR(64)        NOT NULL COMMENT '文件内容 sha256',
    chunk_count     INT UNSIGNED    NOT NULL COMMENT '切出的块数，0 也要记录',
    embedding_model VARCHAR(128)    NOT NULL COMMENT '索引时的 embedding 模型，变化即全量',
    chunk_config    VARCHAR(64)     NOT NULL COMMENT '切块参数快照，变化即全量',
    indexed_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_document_path (document_path)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
