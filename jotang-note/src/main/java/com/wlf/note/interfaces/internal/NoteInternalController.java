package com.wlf.note.interfaces.internal;

import com.wlf.note.application.query.NoteSearchQuery;
import com.wlf.note.application.result.NoteFileResult;
import com.wlf.note.application.service.NoteQueryService;
import com.wlf.note.interfaces.internal.assembler.InternalNoteAssembler;
import com.wlf.note.interfaces.internal.dto.InternalNoteFileResponse;
import com.wlf.note.interfaces.internal.dto.InternalNoteSearchHit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 供 ai-service 调用的内部接口。网关不暴露，另由服务令牌保护。 */
@RestController
@RequestMapping("/internal/notes")
public class NoteInternalController {

    private final NoteQueryService noteQueryService;
    private final InternalNoteAssembler internalNoteAssembler;

    public NoteInternalController(NoteQueryService noteQueryService,
                                  InternalNoteAssembler internalNoteAssembler) {
        this.noteQueryService = noteQueryService;
        this.internalNoteAssembler = internalNoteAssembler;
    }

    @GetMapping("/{noteId}/file")
    public InternalNoteFileResponse requireOwnFile(@PathVariable Long noteId,
                                                   @RequestParam Long userId) {
        return internalNoteAssembler.toResponse(noteQueryService.requireOwnFile(noteId, userId));
    }

    @GetMapping("/search")
    public List<InternalNoteSearchHit> search(@RequestParam String title,
                                              @RequestParam int limit) {
        return noteQueryService.search(new NoteSearchQuery(title, limit))
                .stream()
                .map(internalNoteAssembler::toResponse)
                .toList();
    }

    @GetMapping("/{noteId}/readable-file")
    public InternalNoteFileResponse readableOnlineFile(@PathVariable Long noteId) {
        NoteFileResult file = noteQueryService.readableOnlineFile(noteId);
        return internalNoteAssembler.toResponse(file);
    }
}
