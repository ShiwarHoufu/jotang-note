package com.wlf.catalog;

import com.wlf.catalog.dto.CollegeResponse;
import com.wlf.catalog.dto.CourseResponse;
import com.wlf.catalog.dto.TagResponse;
import com.wlf.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学院 / 课程 / 标签的只读查询接口。见《概要设计》§5.3。
 */
@RestController
public class CatalogController {

    private final CollegeService collegeService;
    private final CourseService courseService;
    private final TagService tagService;

    public CatalogController(CollegeService collegeService,
                             CourseService courseService,
                             TagService tagService) {
        this.collegeService = collegeService;
        this.courseService = courseService;
        this.tagService = tagService;
    }

    /**
     * 学院列表。不需要登录。<br>
     * 注册表单与筛选的下拉数据源。
     */
    @GetMapping("/api/colleges")
    public ApiResponse<List<CollegeResponse>> colleges() {
        return ApiResponse.ok(collegeService.list());
    }

    /**
     * 课程列表。{@code keyword} 按课程名模糊匹配，不传则返回全部。<br>
     * 需要登录：注册页用不到课程下拉框
     */
    @GetMapping("/api/courses")
    public ApiResponse<List<CourseResponse>> courses(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(courseService.list(keyword));
    }

    /**
     * 标签列表。筛选栏与上传表单的候选标签。<br>
     * 需要登录。
     */
    @GetMapping("/api/tags")
    public ApiResponse<List<TagResponse>> tags() {
        return ApiResponse.ok(tagService.list());
    }
}
