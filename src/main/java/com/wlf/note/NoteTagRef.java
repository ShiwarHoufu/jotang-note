package com.wlf.note;

import lombok.Getter;
import lombok.Setter;

/**
 * 「某篇笔记的一个标签」，{@link NoteMapper#selectTagRefsByNoteIds} 的返回类型。
 *
 * <p>比详情那条单笔记查询多一个 {@code noteId}：列表要一次取回一整页笔记的标签，
 * 装配时必须知道每个标签属于哪一篇，否则只能按行顺序猜。
 *
 * <p>用 setter 回填而不是 record：字段名与别名对得上就行（{@code note_id → noteId}），
 * 不依赖「构造器参数按位置匹配」——三列里有两列是 {@code Long}，位置一旦写反
 * （{@code note_id} 与 {@code tag_id} 互换）类型检查拦不住，但按名字回填会直接暴露成 null。
 */
@Getter
@Setter
public class NoteTagRef {

    private Long noteId;
    private Long tagId;
    private String tagName;
}
