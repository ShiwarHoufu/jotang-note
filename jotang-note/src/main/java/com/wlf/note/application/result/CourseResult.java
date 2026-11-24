package com.wlf.note.application.result;

import com.wlf.note.domain.catalog.Course;

/** 课程结果。 */
public record CourseResult(Long id, String name) {

    public static CourseResult from(Course course) {
        return new CourseResult(course.id(), course.name());
    }
}
