package com.wlf.user.application.result;

import com.wlf.user.domain.college.College;

/** 学院用例出参。 */
public record CollegeResult(Long id, String name) {

    public static CollegeResult from(College college) {
        return new CollegeResult(college.id(), college.name());
    }
}
