package com.wlf.note.domain.catalog;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import com.wlf.note.domain.catalog.model.TagName;

/** 标签，名称唯一。 */
public final class Tag {

    private Long id;
    private final String name;

    private Tag(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public static Tag create(TagName name) {
        return new Tag(null, name.value());
    }

    public static Tag reconstitute(Long id, TagName name) {
        return new Tag(id, name.value());
    }

    public void assignId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "标签 id 不合法");
        }
        if (this.id != null) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "标签 id 已存在");
        }
        this.id = id;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }
}
