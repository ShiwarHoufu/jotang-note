package com.wlf.note.application.note.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 「这是谁的」与「现在什么状态」两列，{@link NoteMapper#selectOwnershipForUpdate} 的返回类型。
 */
@Getter
@Setter
public class NoteOwnershipRow {

    /** 上传者。与 JWT 里的当前用户比对，不一致就是 40300 */
    private Long uploaderId;

    /** {@code ONLINE} / {@code OFFLINE} / {@code DELETED} 的原始字符串，由调用方转成 {@link NoteStatus} */
    private String status;
}
