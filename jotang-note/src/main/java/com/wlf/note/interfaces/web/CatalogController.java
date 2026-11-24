package com.wlf.note.interfaces.web;

import com.wlf.common.ApiResponse;
import com.wlf.note.application.service.CourseService;
import com.wlf.note.application.service.TagService;
import com.wlf.note.interfaces.web.assembler.CatalogAssembler;
import com.wlf.note.interfaces.web.dto.CourseResponse;
import com.wlf.note.interfaces.web.dto.TagResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 课程 / 标签的只读查询接口。 */
@RestController
public class CatalogController {

    private final CourseService courseService;
    private final TagService tagService;
    private final CatalogAssembler catalogAssembler;

    public CatalogController(CourseService courseService,
                             TagService tagService,
                             CatalogAssembler catalogAssembler) {
        this.courseService = courseService;
        this.tagService = tagService;
        this.catalogAssembler = catalogAssembler;
    }

    @GetMapping("/api/courses")
    public ApiResponse<List<CourseResponse>> courses(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(courseService.list(keyword).stream()
                .map(catalogAssembler::toResponse)
                .toList());
    }

    @GetMapping("/api/tags")
    public ApiResponse<List<TagResponse>> tags() {
        return ApiResponse.ok(tagService.list().stream()
                .map(catalogAssembler::toResponse)
                .toList());
    }
}
