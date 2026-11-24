package com.wlf.note.application.command;

import com.wlf.note.domain.note.NoteStatus;

/** 管理员下架 / 恢复。 */
public record ChangeNoteStatusCommand(Long noteId, NoteStatus target) {
}
