package com.wlf.catalog.dto;

import com.wlf.entity.Course;

/**
 * 课程出参。
 *
 * <p>不含学院：学院挂在用户身上而不是课程上
 */
public record CourseResponse(Long id, String name) {

    public static CourseResponse from(Course course) {
        return new CourseResponse(course.getId(), course.getName());
    }
}
