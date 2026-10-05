package com.wlf.catalog.dto;

import com.wlf.entity.College;

/**
 * 学院出参。见《概要设计》§5.3。
 *
 * <p>id 与 name 都要：前端拿 name 渲染下拉项，拿 id 作为注册时提交的 collegeId。
 * 只给 name 的话前端还得反查 id，只给 id 的话渲染不出可读的选项。
 */
public record CollegeResponse(Long id, String name) {

    public static CollegeResponse from(College college) {
        return new CollegeResponse(college.getId(), college.getName());
    }
}
