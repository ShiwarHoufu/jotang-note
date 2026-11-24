package com.wlf.note.domain.note.query;

import com.wlf.note.domain.note.NoteSort;

/** 公开列表的查询条件。 */
public record NoteListCriteria(int page,
                               int size,
                               Long courseId,
                               Long tagId,
                               String keyword,
                               NoteSort sort) {
}
