package com.wlf.ai.rag.ingest.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** 知识库文档指纹的读写：记录“哪篇文档在什么配置下索引完成了”。 */
@Repository
public class KbDocumentStateStore {

    private final KbDocumentStateMapper mapper;

    public KbDocumentStateStore(KbDocumentStateMapper mapper) {
        this.mapper = mapper;
    }

    /** 当前 embedding 模型与切块参数下已索引的文档指纹：路径 → 内容 sha256；配置不同的行不参与跳过。 */
    public Map<String, String> findHashes(String embeddingModel, String chunkConfig) {
        return mapper.selectList(Wrappers.<KbDocumentStatePO>lambdaQuery()
                        .select(KbDocumentStatePO::getDocumentPath, KbDocumentStatePO::getContentHash)
                        .eq(KbDocumentStatePO::getEmbeddingModel, embeddingModel)
                        .eq(KbDocumentStatePO::getChunkConfig, chunkConfig))
                .stream()
                .collect(Collectors.toMap(
                        KbDocumentStatePO::getDocumentPath,
                        KbDocumentStatePO::getContentHash));
    }

    /** 向量库写入成功后调用：状态行是“索引完成”的提交标记。 */
    public void save(String documentPath, String documentId, String contentHash, int chunkCount,
                     String embeddingModel, String chunkConfig) {
        mapper.upsert(documentPath, documentId, contentHash, chunkCount, embeddingModel, chunkConfig);
    }

    /** 删除已不在知识库目录里的文档状态；目录为空时清空整表。 */
    public void deleteNotIn(Collection<String> currentPaths) {
        if (currentPaths.isEmpty()) {
            mapper.delete(Wrappers.emptyWrapper());
            return;
        }
        mapper.delete(Wrappers.<KbDocumentStatePO>lambdaQuery()
                .notIn(KbDocumentStatePO::getDocumentPath, currentPaths));
    }
}
