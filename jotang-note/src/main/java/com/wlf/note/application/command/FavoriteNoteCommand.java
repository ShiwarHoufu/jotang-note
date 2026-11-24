package com.wlf.note.application.command;

/** 收藏 / 取消收藏。 */
public record FavoriteNoteCommand(Long userId, Long noteId) {
}
