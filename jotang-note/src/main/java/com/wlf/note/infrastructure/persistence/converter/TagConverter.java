package com.wlf.note.infrastructure.persistence.converter;

import com.wlf.note.domain.catalog.Tag;
import com.wlf.note.domain.catalog.model.TagName;
import com.wlf.note.infrastructure.persistence.po.TagPO;

/** Tag ↔ tag 行。 */
public final class TagConverter {

    private TagConverter() {
    }

    public static Tag toDomain(TagPO po) {
        return Tag.reconstitute(po.getId(), new TagName(po.getName()));
    }

    public static TagPO toPO(Tag tag) {
        TagPO po = new TagPO();
        po.setId(tag.id());
        po.setName(tag.name());
        return po;
    }
}
