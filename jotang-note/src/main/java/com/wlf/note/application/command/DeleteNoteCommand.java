package com.wlf.note.application.command;

/** 软删除自己的笔记。 */
public record DeleteNoteCommand(Long noteId, Long uploaderId) {
}
