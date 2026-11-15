package com.wlf.note.application.note.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 这篇笔记的附件在哪、谁能读它。{@link NoteMapper#selectFileOwnership} 的返回类型。
 */
@Getter
@Setter
public class NoteFileOwnershipRow {

    /** 上传者。与 JWT 里的当前用户比对，不一致就是 40300 */
    private Long uploaderId;

    /** {@code ONLINE} / {@code OFFLINE} / {@code DELETED} 的原始字符串，由调用方转成 {@link NoteStatus} */
    private String status;

    /** OSS 对象键，供 {@code StorageService#open} 读取字节 */
    private String storageKey;

    /** 字节数。*/
    private Long size;

    /**
     * 服务端判定的类型
     *
     * <p>ai 用它回答两个问题：这份附件能不能摘要（是不是光栅图），以及该以什么 MIME 交给模型。
     */
    private String contentType;
}
