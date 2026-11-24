package com.wlf.note.application.command;

import java.util.List;

/** 编辑笔记元数据与标签。 */
public record UpdateNoteCommand(Long noteId,
                                Long editorId,
                                String title,
                                String summary,
                                String teacher,
                                Long courseId,
                                List<String> tags) {
}
