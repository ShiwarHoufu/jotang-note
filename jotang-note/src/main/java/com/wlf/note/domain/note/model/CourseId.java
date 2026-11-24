package com.wlf.note.domain.note.model;

/** 课程引用：只保证 id 形状合法，课程是否存在由 CourseRepository 判断。 */
public record CourseId(Long value) {

    public static final String RULE_MESSAGE = "课程 id 不合法";

    public CourseId {
        if (value == null || value <= 0) {
            throw NoteViolation.invalid("courseId", RULE_MESSAGE);
        }
    }
}
