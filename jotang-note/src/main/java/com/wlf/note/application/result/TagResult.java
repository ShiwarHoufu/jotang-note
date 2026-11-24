package com.wlf.note.application.result;

import com.wlf.note.domain.catalog.Tag;

/** 标签结果。 */
public record TagResult(Long id, String name) {

    public static TagResult from(Tag tag) {
        return new TagResult(tag.id(), tag.name());
    }
}
