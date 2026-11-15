package com.wlf.ai.port;

/**
 * AI 侧需要的附件位置与元信息。归属与可见性由提供方判定，调用方只消费结果。
 */
public record NoteFileRef(String storageKey, Long size, String contentType) {
}
