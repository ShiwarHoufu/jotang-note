package com.wlf.note.application.query;

import com.wlf.note.domain.note.NoteSort;

/** 公开列表查询。 */
public record NoteListQuery(int page,
                            int size,
                            Long courseId,
                            Long tagId,
                            String keyword,
                            NoteSort sort) {
}
