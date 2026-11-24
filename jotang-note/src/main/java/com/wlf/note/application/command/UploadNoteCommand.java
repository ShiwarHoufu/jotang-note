package com.wlf.note.application.command;

import java.util.List;

/** 上传笔记。 */
public record UploadNoteCommand(Long uploaderId,
                                UploadedFile file,
                                String title,
                                String summary,
                                String teacher,
                                Long courseId,
                                List<String> tags) {
}
