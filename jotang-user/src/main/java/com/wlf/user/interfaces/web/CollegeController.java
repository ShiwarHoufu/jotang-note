package com.wlf.user.interfaces.web;

import com.wlf.common.ApiResponse;
import com.wlf.user.application.service.CollegeService;
import com.wlf.user.application.result.CollegeResult;
import com.wlf.user.interfaces.web.assembler.CollegeAssembler;
import com.wlf.user.interfaces.web.dto.CollegeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学院列表。注册表单与筛选的下拉数据源，不需要登录。
 */
@RestController
public class CollegeController {

    private final CollegeService collegeService;
    private final CollegeAssembler collegeAssembler;

    public CollegeController(CollegeService collegeService, CollegeAssembler collegeAssembler) {
        this.collegeService = collegeService;
        this.collegeAssembler = collegeAssembler;
    }

    @GetMapping("/api/colleges")
    public ApiResponse<List<CollegeResponse>> colleges() {
        List<CollegeResult> results = collegeService.list();
        return ApiResponse.ok(results.stream()
                .map(collegeAssembler::toResponse)
                .toList());
    }
}
