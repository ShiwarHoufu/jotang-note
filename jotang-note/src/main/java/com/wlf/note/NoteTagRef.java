package com.wlf.note;

import lombok.Getter;
import lombok.Setter;

/**
 * 某篇笔记的一个标签.
 * {@link NoteMapper#selectTagRefsByNoteIds} 的返回类型。
 *
 * <p>比详情那条单笔记查询多一个 {@code noteId}：列表要一次取回一整页笔记的标签，
 * 装配时必须知道每个标签属于哪一篇，否则只能按行顺序猜。
 */
@Getter
@Setter
public class NoteTagRef {

    private Long noteId;
    private Long tagId;
    private String tagName;
}
