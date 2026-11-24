package com.wlf.note.infrastructure.persistence.converter;

import com.wlf.note.domain.catalog.Course;
import com.wlf.note.infrastructure.persistence.po.CoursePO;

/** Course ↔ course 行。 */
public final class CourseConverter {

    private CourseConverter() {
    }

    public static Course toDomain(CoursePO po) {
        return Course.reconstitute(po.getId(), po.getName(),
                po.getIsOther() != null && po.getIsOther() == 1);
    }
}
