package com.wlf.note.interfaces.internal;

/**
 * 内部接口返回的附件位置与元信息。
 */
public record InternalNoteFileResponse(String storageKey, Long size, String contentType) {
}
