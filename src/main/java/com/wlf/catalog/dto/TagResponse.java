package com.wlf.catalog.dto;

import com.wlf.entity.Tag;

/**
 * 标签出参。
 *
 * <p>id 与 name 都要：前端拿 name 渲染标签，拿 id 作为筛选与上传时提交的 tagId。
 */
public record TagResponse(Long id, String name) {

    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName());
    }
}
