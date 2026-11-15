package com.wlf.note.interfaces.internal;

import com.wlf.note.application.note.NoteService;
import com.wlf.note.application.note.dto.NoteFileOwnershipRow;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 供 ai-service 调用的内部接口。网关不暴露，另由服务令牌保护。
 */
@RestController
@RequestMapping("/internal/notes")
public class NoteInternalController {

    private final NoteService noteService;

    public NoteInternalController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping("/{noteId}/file")
    public InternalNoteFileResponse requireOwnFile(@PathVariable Long noteId,
                                                   @RequestParam Long userId) {
        return toFile(noteService.requireOwnFile(noteId, userId));
    }

    @GetMapping("/search")
    public List<InternalNoteSearchHit> search(@RequestParam String title,
                                              @RequestParam int limit) {
        return noteService.findOnlineByExactTitle(title, limit).stream()
                .map(hit -> new InternalNoteSearchHit(hit.noteId(), hit.title(), hit.teacher(), hit.summary()))
                .toList();
    }

    @GetMapping("/{noteId}/readable-file")
    public InternalNoteFileResponse readableOnlineFile(@PathVariable Long noteId) {
        return toFile(noteService.readableOnlineFile(noteId));
    }

    private static InternalNoteFileResponse toFile(NoteFileOwnershipRow row) {
        return new InternalNoteFileResponse(row.getStorageKey(), row.getSize(), row.getContentType());
    }
}
