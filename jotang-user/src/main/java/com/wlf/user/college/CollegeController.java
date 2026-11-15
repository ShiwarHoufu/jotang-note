package com.wlf.user.college;

import com.wlf.common.ApiResponse;
import com.wlf.user.college.dto.CollegeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学院列表。注册表单与筛选的下拉数据源，不需要登录。
 */
@RestController
public class CollegeController {

    private final CollegeService collegeService;

    public CollegeController(CollegeService collegeService) {
        this.collegeService = collegeService;
    }

    @GetMapping("/api/colleges")
    public ApiResponse<List<CollegeResponse>> colleges() {
        return ApiResponse.ok(collegeService.list());
    }
}
