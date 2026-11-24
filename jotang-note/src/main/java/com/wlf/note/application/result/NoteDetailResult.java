package com.wlf.note.application.result;

import com.wlf.note.domain.note.NoteStatus;
import com.wlf.note.domain.note.PreviewMode;

import java.time.LocalDateTime;
import java.util.List;

/** 笔记详情。 */
public record NoteDetailResult(Long id,
                               String title,
                               String summary,
                               String teacher,
                               NoteStatus status,
                               CourseResult course,
                               UploaderResult uploader,
                               List<TagResult> tags,
                               long viewCount,
                               long downloadCount,
                               long favoriteCount,
                               LocalDateTime createdAt,
                               LocalDateTime updatedAt,
                               FileInfo file,
                               boolean isFavorited) {

    /** 非 ONLINE 时整个 file 为 null。 */
    public record FileInfo(String originalName, long size, String contentType, PreviewMode previewMode) {
    }
}
