package com.wlf.ai.infrastructure.vectorstore;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.List;

/** 知识库块在向量库中的读写入口：屏蔽 Spring AI 的检索参数与批写细节。 */
@Component
public class KbVectorStore {

    /** 低于这个相似度的块不返回，避免把无关内容塞给模型 */
    private static final double SIMILARITY_THRESHOLD = 0.5;

    private final VectorStore vectorStore;

    public KbVectorStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** 按语义检索有效文档的块。 */
    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .filterExpression(new FilterExpressionBuilder()
                        .eq(KbChunkFields.STATUS, KbChunkFields.STATUS_ACTIVE)
                        .build())
                .build());
    }

    /** 覆盖一篇文档的全部块；先删旧块，避免块数变少时留下多余的点。 */
    public void replaceDocument(String documentId, List<Document> chunks) {
        vectorStore.delete(new FilterExpressionBuilder()
                .eq(KbChunkFields.DOCUMENT_ID, documentId)
                .build());
        vectorStore.add(chunks);
    }
}
