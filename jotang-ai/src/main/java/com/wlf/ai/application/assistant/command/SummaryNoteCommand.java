package com.wlf.ai.application.assistant.command;

/** 编辑场景的用例输入。 */
public record SummaryNoteCommand(Long userId, Long noteId, String title, String teacher) {
}
