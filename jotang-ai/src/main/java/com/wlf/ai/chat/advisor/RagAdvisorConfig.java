package com.wlf.ai.chat.advisor;

import com.wlf.ai.infrastructure.vectorstore.KbChunkFields;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** RAG 问答的检索增强 Advisor。 */
@Configuration
public class RagAdvisorConfig {

    private static final PromptTemplate AUGMENT_PROMPT = new PromptTemplate("""
            以下是知识库中检索到的内容：

            ---------------------
            {context}
            ---------------------

            请优先依据以上内容回答用户的问题；内容不足时可以用通用常识补充，
            但涉及学校的具体规定、时间、地点、数字等信息时不要编造。
            不要复述这段说明。

            用户的问题：{query}
            """);

    /**
     * 检索增强Advisor：检索 topK 片段，拼进用户消息，再交给对话模型。
     */
    @Bean
    public RetrievalAugmentationAdvisor ragAdvisor(VectorStore vectorStore) {
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(VectorStoreDocumentRetriever.builder()
                        .vectorStore(vectorStore)
                        .topK(4)
                        .similarityThreshold(0.5)
                        .filterExpression(new FilterExpressionBuilder()
                                .eq(KbChunkFields.STATUS, KbChunkFields.STATUS_ACTIVE)
                                .build())
                        .build())
                .queryAugmenter(ContextualQueryAugmenter.builder()
                        .promptTemplate(AUGMENT_PROMPT)
                        // 检索为空时保留原问题，让模型照常回答，而不是替换成固定的拒答文案
                        .allowEmptyContext(true)
                        .build())
                .build();
    }
}
