package com.wlf.ai.infrastructure.client.note;

/**
 * note 内部接口的附件响应。
 */
public record InternalNoteFile(String storageKey, Long size, String contentType) {
}
