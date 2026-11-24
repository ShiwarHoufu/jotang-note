package com.wlf.note.interfaces.internal.assembler;

import com.wlf.note.application.result.NoteFileResult;
import com.wlf.note.application.result.NoteSearchHitResult;
import com.wlf.note.interfaces.internal.dto.InternalNoteFileResponse;
import com.wlf.note.interfaces.internal.dto.InternalNoteSearchHit;
import org.springframework.stereotype.Component;

/** 内部接口的 Result → Response 映射。 */
@Component
public class InternalNoteAssembler {

    public InternalNoteFileResponse toResponse(NoteFileResult result) {
        return new InternalNoteFileResponse(result.storageKey(), result.size(), result.contentType());
    }

    public InternalNoteSearchHit toResponse(NoteSearchHitResult result) {
        return new InternalNoteSearchHit(result.noteId(), result.title(), result.teacher(), result.summary());
    }
}
