package com.wlf.note.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.note.domain.catalog.Course;
import com.wlf.note.domain.catalog.CourseRepository;
import com.wlf.note.infrastructure.persistence.converter.CourseConverter;
import com.wlf.note.infrastructure.persistence.po.CoursePO;
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
                Wrappers.<CoursePO>lambdaQuery().eq(CoursePO::getId, id));
    }

    @Override
    public List<Course> search(String keyword) {
        return courseMapper.selectList(Wrappers.<CoursePO>lambdaQuery()
                        .like(keyword != null, CoursePO::getName, keyword)
                        // 内置「其他」压到末位；其余按录入顺序，不按中文名排序
                        .orderByAsc(CoursePO::getIsOther)
                        .orderByAsc(CoursePO::getId))
                .stream()
                .map(CourseConverter::toDomain)
                .toList();
    }
}
