package com.wlf.note.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.note.domain.catalog.Course;
import com.wlf.note.domain.catalog.CourseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 课程读取端口的 MyBatis-Plus 实现。
 */
@Repository
public class CourseRepositoryImpl implements CourseRepository {

    private final CourseMapper courseMapper;

    public CourseRepositoryImpl(CourseMapper courseMapper) {
        this.courseMapper = courseMapper;
    }

    @Override
    public boolean existsById(Long id) {
        return id != null && courseMapper.exists(
                Wrappers.<Course>lambdaQuery().eq(Course::getId, id));
    }

    @Override
    public List<Course> search(String keyword) {
        return courseMapper.selectList(Wrappers.<Course>lambdaQuery()
                .like(keyword != null, Course::getName, keyword)
                // 内置「其他」压到末位；其余按录入顺序，不按中文名排序
                .orderByAsc(Course::getIsOther)
                .orderByAsc(Course::getId));
    }
}
