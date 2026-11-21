package com.wlf.ai.infrastructure.vectorstore;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

/** 知识库块在向量库中的读写入口：屏蔽检索参数细节。 */
@Component
public class KbVectorStore {

    private final VectorStore vectorStore;

    public KbVectorStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** 按语义检索有效文档的块。 */
    public List<Document> search(String query, int topK, double threshHold) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshHold)
                .filterExpression(new FilterExpressionBuilder()
                        .eq(KbChunkFields.STATUS, KbChunkFields.STATUS_ACTIVE)
                        .build())
                .build());
    }

    /** 追加一批块；
     * point id 相同的块会被覆盖。 */
    public void append(List<Document> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        vectorStore.add(chunks);
    }

    /** 覆盖一篇文档的全部块；
     * 先删旧块，避免块数变少时留下多余的点。 */
    public void replaceDocument(String documentId, List<Document> chunks) {
        vectorStore.delete(new FilterExpressionBuilder()
                .eq(KbChunkFields.DOCUMENT_ID, documentId)
                .build());
        vectorStore.add(chunks);
    }
}
