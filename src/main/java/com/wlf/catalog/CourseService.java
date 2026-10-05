package com.wlf.catalog;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.dto.CourseResponse;
import com.wlf.entity.Course;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 课程查询。见《概要设计》§5.3。
 *
 * <p>只有读，没有增删改：课程是预置数据，用户不能自建（§3.2）。
 */
@Service
public class CourseService {

    private final CourseMapper courseMapper;

    public CourseService(CourseMapper courseMapper) {
        this.courseMapper = courseMapper;
    }

    /**
     * 课程列表, 不分页;
     * @param keyword 匹配课程名；null / 空白视为不过滤
     */
    public List<CourseResponse> list(String keyword) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;

        return courseMapper.selectList(
                        Wrappers.<Course>lambdaQuery()
                                .like(kw != null, Course::getName, kw)
                                // 内置「其他」压到末位，作为兜底项出现在下拉框最后。
                                // 其余按 id 升序（录入顺序）——不能按 name 排：
                                // utf8mb4_0900_ai_ci 对中文是按 Unicode 码点排序，不是拼音序，排了也没意义。
                                .orderByAsc(Course::getIsOther)
                                .orderByAsc(Course::getId))
                .stream()
                .map(CourseResponse::from)
                .toList();
    }
}
