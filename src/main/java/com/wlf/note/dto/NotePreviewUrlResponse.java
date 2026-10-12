package com.wlf.note.dto;

/**
 * 图片预览的出参：OSS 签名 URL。见《概要设计》§5.4、§6.2。
 *
 * <p>包成对象而不是直接返 {@code String}，理由同 {@link NoteCreatedResponse}：
 * 响应体的 {@code data} 保持恒为 JSON 对象，前端拦截器与 Apifox 示例不必为单个接口破例。
 */
public record NotePreviewUrlResponse(String url) {
}
