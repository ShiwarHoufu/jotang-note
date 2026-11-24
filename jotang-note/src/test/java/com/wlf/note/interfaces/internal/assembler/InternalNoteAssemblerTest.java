package com.wlf.note.interfaces.internal.assembler;

import com.wlf.note.application.result.NoteFileResult;
import com.wlf.note.application.result.NoteSearchHitResult;
import com.wlf.note.interfaces.internal.dto.InternalNoteFileResponse;
import com.wlf.note.interfaces.internal.dto.InternalNoteSearchHit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalNoteAssemblerTest {

    private final InternalNoteAssembler assembler = new InternalNoteAssembler();

    @Test
    void mapsFileResult() {
        InternalNoteFileResponse response = assembler.toResponse(
                new NoteFileResult("notes/1/a.md", 100L, "text/markdown"));

        assertThat(response.storageKey()).isEqualTo("notes/1/a.md");
        assertThat(response.size()).isEqualTo(100L);
        assertThat(response.contentType()).isEqualTo("text/markdown");
    }

    @Test
    void mapsSearchHit() {
        InternalNoteSearchHit response = assembler.toResponse(
                new NoteSearchHitResult(1L, "标题", "老师", "简介"));

        assertThat(response.noteId()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("标题");
    }
}
