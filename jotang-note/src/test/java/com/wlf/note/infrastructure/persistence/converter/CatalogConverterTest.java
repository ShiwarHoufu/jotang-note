package com.wlf.note.infrastructure.persistence.converter;

import com.wlf.note.domain.catalog.Course;
import com.wlf.note.domain.catalog.Tag;
import com.wlf.note.domain.catalog.model.TagName;
import com.wlf.note.infrastructure.persistence.po.CoursePO;
import com.wlf.note.infrastructure.persistence.po.TagPO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogConverterTest {

    @Test
    void convertsCourse() {
        CoursePO po = new CoursePO();
        po.setId(1L);
        po.setName("其他");
        po.setIsOther(1);

        Course course = CourseConverter.toDomain(po);

        assertThat(course.id()).isEqualTo(1L);
        assertThat(course.name()).isEqualTo("其他");
        assertThat(course.isOther()).isTrue();
    }

    @Test
    void convertsTagBothWays() {
        TagPO po = new TagPO();
        po.setId(1L);
        po.setName("期末复习");

        Tag tag = TagConverter.toDomain(po);
        assertThat(tag.id()).isEqualTo(1L);
        assertThat(tag.name()).isEqualTo("期末复习");

        TagPO back = TagConverter.toPO(tag);
        assertThat(back.getId()).isEqualTo(1L);
        assertThat(back.getName()).isEqualTo("期末复习");
    }

    @Test
    void convertsNewTagToPO() {
        Tag tag = Tag.create(new TagName("新标签"));

        TagPO po = TagConverter.toPO(tag);

        assertThat(po.getId()).isNull();
        assertThat(po.getName()).isEqualTo("新标签");
    }
}
