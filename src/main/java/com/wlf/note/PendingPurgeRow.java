package com.wlf.note;

import lombok.Getter;
import lombok.Setter;

/**
 * 「哪篇笔记、对象在 OSS 的哪个键」两列，{@link NoteMapper#selectPendingPurge} 的返回类型。
 *
 * <p>不复用 {@code NoteFile} 实体：那张表还有 {@code original_name} / {@code size} /
 * {@code content_type}，对账一个都用不上。
 */
@Getter
@Setter
public class PendingPurgeRow {

    /** 笔记 id，用于删完之后把 {@code note_file.is_purged} 置成 1 */
    private Long noteId;

    /** OSS 对象键，{@code notes/yyyy/MM/{uuid}.{ext}} */
    private String storageKey;
}
