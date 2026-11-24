package com.wlf.note.application.result;

/** 附件的 OSS 位置与类型。 */
public record NoteFileResult(String storageKey, long size, String contentType) {
}
