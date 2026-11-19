package com.wlf.ai.chat.tool;

import com.wlf.ai.infrastructure.note.NoteGateway;
import com.wlf.ai.infrastructure.note.NoteHit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;

/** 按标题检索笔记；每次请求新建一个实例。 */
@Slf4j
public class NoteSearchTool {

    /** 同名笔记最多回几条。标题在库上没有唯一约束 */
    private static final int MAX_HITS = 5;

    private final Long userId;
    private final NoteGateway noteGateway;

    public NoteSearchTool(Long userId, NoteGateway noteGateway) {
        this.userId = userId;
        this.noteGateway = noteGateway;
    }

    @Tool(name = "search_notes_by_title", description = NoteToolPrompts.SEARCH_NOTES_DESCRIPTION)
    public List<NoteHit> searchNotes(
            @ToolParam(description = "笔记的完整标题，逐字照抄，不要带《》书名号") String title) {
        String normalized = normalizeTitle(title);
        List<NoteHit> hits = normalized.isEmpty()
                ? List.of()
                : noteGateway.searchByTitle(normalized, MAX_HITS);
        // 精确匹配要求模型逐字给出标题，它做不到时这条日志是唯一的线索
        log.info("检索笔记：userId={}，标题=[{}]，命中 {} 条", userId, normalized, hits.size());
        return hits;
    }

    /** 模型常照搬用户话里的书名号。只剥首尾——标题中间真的出现《》不该动 */
    private static String normalizeTitle(String raw) {
        if (raw == null) {
            return "";
        }
        String title = raw.strip();
        if (title.length() >= 2) {
            char first = title.charAt(0);
            char last = title.charAt(title.length() - 1);
            boolean quoted = (first == '《' && last == '》')
                    || (first == '“' && last == '”')
                    || (first == '"' && last == '"')
                    || (first == '\'' && last == '\'');
            if (quoted) {
                title = title.substring(1, title.length() - 1).strip();
            }
        }
        return title;
    }
}
