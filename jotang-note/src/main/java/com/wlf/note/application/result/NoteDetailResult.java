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

    /** 详情可见即返回：ONLINE 所有人，OFFLINE 仅本人/管理员；DELETED 不可达。 */
    public record FileInfo(String originalName, long size, String contentType, PreviewMode previewMode) {
    }
}
