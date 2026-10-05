package com.wlf.catalog.dto;

import com.wlf.entity.Course;

/**
 * 课程出参。见《概要设计》§5.3。
 *
 * <p>不含 {@code isOther}：内置「其他」由 Service 排到末位，前端照序渲染即可，
 * 不需要自己判断哪条是兜底项。该字段属于「系统怎么处理这条记录」而非「课程是什么」，
 * 露出去只会诱使前端重复实现后端的规则。
 *
 * <p>将来真需要时再加：响应体多一个字段是非破坏性变更，而加上再拿掉是破坏性的，
 * 所以这里宁可从窄。
 *
 * <p>不含学院：学院挂在用户身上而不是课程上（决策 D8），见《概要设计》§3.3。
 */
public record CourseResponse(Long id, String name) {

    public static CourseResponse from(Course course) {
        return new CourseResponse(course.getId(), course.getName());
    }
}
