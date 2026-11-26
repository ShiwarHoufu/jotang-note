package com.wlf.ai.infrastructure.vectorstore;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
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
     * 先删旧块，块数变少或解析不出内容时都不会留下多余的点。 */
    public void replaceDocument(String documentId, List<Document> chunks) {
        vectorStore.delete(new FilterExpressionBuilder()
                .eq(KbChunkFields.DOCUMENT_ID, documentId)
                .build());
        if (chunks.isEmpty()) {
            return;
        }
        vectorStore.add(chunks);
    }

    /** 删除不在本次扫描结果里的文档块：文件被删除或改名后，旧块不再参与检索。 */
    public void removeStale(Collection<String> currentPaths) {
        // NIN 不接受空列表；空串不可能是合法相对路径，空目录时等价于删除所有知识库块
        List<Object> kept = currentPaths.isEmpty()
                ? List.of("")
                : new ArrayList<>(currentPaths);
        vectorStore.delete(new FilterExpressionBuilder()
                .nin(KbChunkFields.DOCUMENT_PATH, kept)
                .build());
    }
}
