package com.wlf.note.application.result;

import java.time.LocalDateTime;
import java.util.List;

/** 管理员「已下架笔记」列表的一项。 */
public record AdminNoteItemResult(Long id,
                                  String title,
                                  CourseResult course,
                                  UploaderResult uploader,
                                  List<TagResult> tags,
                                  long viewCount,
                                  long downloadCount,
                                  long favoriteCount,
                                  LocalDateTime createdAt,
                                  LocalDateTime updatedAt) {
}
