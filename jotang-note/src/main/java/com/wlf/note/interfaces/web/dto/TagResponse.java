package com.wlf.note.interfaces.web.dto;

/**
 * 标签出参。
 *
 * <p>id 与 name 都要：前端拿 name 渲染标签，拿 id 作为筛选与上传时提交的 tagId。
 */
public record TagResponse(Long id, String name) {
}
