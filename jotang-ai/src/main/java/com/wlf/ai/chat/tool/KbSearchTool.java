package com.wlf.ai.chat.tool;

import com.wlf.ai.infrastructure.vectorstore.KbVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/** 检索学校文件知识库；每次请求新建一个实例。 */
@Slf4j
public class KbSearchTool {

    /** 单轮最多回几块。一块约 600 token，4 块已够回答一个问题 */
    private static final int MAX_HITS = 4;

    private final Long userId;
    private final KbVectorStore kbVectorStore;

    public KbSearchTool(Long userId, KbVectorStore kbVectorStore) {
        this.userId = userId;
        this.kbVectorStore = kbVectorStore;
    }

    @Tool(name = "search_knowledge_base", description =
            "检索学校官方文件知识库，返回相关的文件名、来源与片段。"
            + "用户询问学校事务、政策、培养方案、校历、院系信息时使用。"
            + "查不到就据实告诉用户知识库里没有，不要编造学校的具体规定。")
    public List<KbHit> search(
            @ToolParam(description = "要检索的问题或关键词，保留学校、专业、事项等关键实体") String query) {
        String normalized = query == null ? "" : query.strip();
        if (normalized.isEmpty()) {
            return List.of();
        }

        List<KbHit> hits;
        try {
            hits = kbVectorStore.search(normalized, MAX_HITS).stream()
                    .map(KbHit::from)
                    .toList();
        } catch (RuntimeException e) {
            // 检索失败不能拖垮整轮对话：返回空结果，让模型按「没有资料」处理
            log.error("检索知识库失败：userId={}，query=[{}]", userId, normalized, e);
            return List.of();
        }

        log.info("检索知识库：userId={}，query=[{}]，命中 {} 条", userId, normalized, hits.size());
        return hits;
    }
}
