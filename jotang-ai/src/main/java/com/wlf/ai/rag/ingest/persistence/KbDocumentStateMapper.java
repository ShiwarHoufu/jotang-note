package com.wlf.ai.rag.ingest.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** kb_document_state 表的数据访问。 */
@Mapper
public interface KbDocumentStateMapper extends BaseMapper<KbDocumentStatePO> {

    /** 按路径写入状态，已存在则整行覆盖；{@code indexed_at} 不会被 DEFAULT 刷新，显式赋值。 */
    @Insert("""
            INSERT INTO kb_document_state
                (document_path, document_id, content_hash, chunk_count, embedding_model, chunk_config, indexed_at)
            VALUES (#{documentPath}, #{documentId}, #{contentHash}, #{chunkCount}, #{embeddingModel}, #{chunkConfig}, NOW()) AS new
            ON DUPLICATE KEY UPDATE
                document_id     = new.document_id,
                content_hash    = new.content_hash,
                chunk_count     = new.chunk_count,
                embedding_model = new.embedding_model,
                chunk_config    = new.chunk_config,
                indexed_at      = NOW()
            """)
    void upsert(@Param("documentPath") String documentPath,
                @Param("documentId") String documentId,
                @Param("contentHash") String contentHash,
                @Param("chunkCount") int chunkCount,
                @Param("embeddingModel") String embeddingModel,
                @Param("chunkConfig") String chunkConfig);
}
