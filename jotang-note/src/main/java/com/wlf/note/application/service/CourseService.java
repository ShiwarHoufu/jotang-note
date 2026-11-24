package com.wlf.note.application.service;

import com.wlf.note.application.result.CourseResult;
import com.wlf.note.domain.catalog.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 课程查询。
 *
 * <p>只有读，没有增删改：课程是预置数据，用户不能自建。
 */
@Service
public class CourseService {

    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    /**
     * 课程是否存在。笔记上传时用它校验 {@code courseId} 合法性。
     *
     * @param courseId null 视为不存在
     */
    public boolean exists(Long courseId) {
        return courseRepository.existsById(courseId);
    }

    /**
     * 课程列表，不分页。
     *
     * @param keyword 匹配课程名；null / 空白视为不过滤
     */
    public List<CourseResult> list(String keyword) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return courseRepository.search(kw).stream()
                .map(CourseResult::from)
                .toList();
    }
}
