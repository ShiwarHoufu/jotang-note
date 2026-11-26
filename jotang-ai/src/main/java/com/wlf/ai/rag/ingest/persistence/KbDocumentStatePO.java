package com.wlf.ai.rag.ingest.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** kb_document_state 表映射：一篇文档一行，记住上次索引完成时的指纹。 */
@Getter
@Setter
@TableName("kb_document_state")
public class KbDocumentStatePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String documentPath;
    private String documentId;
    private String contentHash;
    private Integer chunkCount;

    /** 索引时的 embedding 模型；与当前配置不一致的行不参与跳过判定 */
    private String embeddingModel;

    /** 索引时的切块参数快照，同上 */
    private String chunkConfig;

    private LocalDateTime indexedAt;
}
