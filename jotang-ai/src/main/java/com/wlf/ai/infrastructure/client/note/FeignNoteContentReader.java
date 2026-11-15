package com.wlf.ai.infrastructure.client.note;

import com.wlf.ai.application.port.out.NoteContentReader;
import com.wlf.ai.application.port.out.NoteFileRef;
import com.wlf.ai.application.port.out.NoteSearchHit;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 把 note 的 HTTP 内部接口适配成 AI 的端口。
 */
@Component
public class FeignNoteContentReader implements NoteContentReader {

    private final NoteClient noteClient;

    public FeignNoteContentReader(NoteClient noteClient) {
        this.noteClient = noteClient;
    }

    @Override
    public NoteFileRef requireOwnFile(Long noteId, Long userId) {
        return toRef(noteClient.requireOwnFile(noteId, userId));
    }

    @Override
    public List<NoteSearchHit> findOnlineByExactTitle(String title, int limit) {
        return noteClient.search(title, limit).stream()
                .map(hit -> new NoteSearchHit(hit.noteId(), hit.title(), hit.teacher(), hit.summary()))
                .toList();
    }

    @Override
    public NoteFileRef readableOnlineFile(Long noteId) {
        return toRef(noteClient.readableOnlineFile(noteId));
    }

    private static NoteFileRef toRef(InternalNoteFile file) {
        return new NoteFileRef(file.storageKey(), file.size(), file.contentType());
    }
}
