package com.wlf.user.domain.user.model;

/** 学院引用：只保证 id 形状合法，学院是否存在由 College 聚合判断。 */
public record CollegeId(Long value) {

    public static final String RULE_MESSAGE = "学院 id 不合法";

    public CollegeId {
        if (value == null || value <= 0) {
            throw UserViolation.invalid("collegeId", RULE_MESSAGE);
        }
    }
}
