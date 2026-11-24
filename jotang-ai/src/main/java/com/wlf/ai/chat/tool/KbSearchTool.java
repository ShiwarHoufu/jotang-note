package com.wlf.ai.chat.tool;

import com.wlf.ai.infrastructure.vectorstore.KbVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 检索学校文件知识库；每次请求新建一个实例。 */
@Slf4j
public class KbSearchTool {

    /** 单轮最多回几块 */
    private static final int MAX_HITS = 4;
    /** 低于这个相似度的块不返回 */
    private static final double SIMILARITY_THRESHOLD = 0.5;

    private final Long userId;
    private final KbVectorStore kbVectorStore;

    /** 本次对话累计命中的块，供评测导出；正文作 key，按首次命中顺序去重 */
    private final Map<String, KbHit> retrievedHits = new LinkedHashMap<>();

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

        List<KbHit> found;
        try {
            found = kbVectorStore.search(normalized, MAX_HITS, SIMILARITY_THRESHOLD).stream()
                    .map(KbHit::from)
                    .toList();
        } catch (RuntimeException e) {
            log.error("检索知识库失败：userId={}，query=[{}]", userId, normalized, e);
            return List.of();
        }

        record(found);
        
        log.info("检索知识库：userId={}，query=[{}]，命中 {} 条", userId, normalized, found.size());
        for (int i = 0; i < found.size(); i++) {
            KbHit hit = found.get(i);
            log.info("命中[{}]：title={}，source={}\n{}", i + 1, hit.title(), hit.source(), hit.text());
        }
        return found;
    }



    /** 本轮对话累计命中的块；返回快照，调用方修改不影响记录。 */
    public List<KbHit> retrievedHits() {
        return List.copyOf(retrievedHits.values());
    }

    private void record(List<KbHit> hits) {
        hits.forEach(hit -> retrievedHits.putIfAbsent(hit.text(), hit));
    }
}
