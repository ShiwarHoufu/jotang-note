package com.wlf.app;

import com.wlf.ai.port.NoteContentReader;
import com.wlf.ai.port.NoteFileRef;
import com.wlf.ai.port.NoteSearchHit;
import com.wlf.note.application.note.dto.NoteFileOwnershipRow;
import com.wlf.note.application.note.NoteService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 单体期的本地适配器：把 AI 端口调用转给笔记服务。
 */
@Component
public class LocalNoteContentReader implements NoteContentReader {

    private final NoteService noteService;

    public LocalNoteContentReader(NoteService noteService) {
        this.noteService = noteService;
    }

    @Override
    public NoteFileRef requireOwnFile(Long noteId, Long userId) {
        return toRef(noteService.requireOwnFile(noteId, userId));
    }

    @Override
    public List<NoteSearchHit> findOnlineByExactTitle(String title, int limit) {
        return noteService.findOnlineByExactTitle(title, limit).stream()
                .map(hit -> new NoteSearchHit(hit.noteId(), hit.title(), hit.teacher(), hit.summary()))
                .toList();
    }

    @Override
    public NoteFileRef readableOnlineFile(Long noteId) {
        return toRef(noteService.readableOnlineFile(noteId));
    }

    private static NoteFileRef toRef(NoteFileOwnershipRow row) {
        return new NoteFileRef(row.getStorageKey(), row.getSize(), row.getContentType());
    }
}
