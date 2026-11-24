package com.wlf.note.interfaces.web.dto;

/**
 * 课程出参。
 *
 * <p>不含学院：学院挂在用户身上而不是课程上
 */
public record CourseResponse(Long id, String name) {
}
