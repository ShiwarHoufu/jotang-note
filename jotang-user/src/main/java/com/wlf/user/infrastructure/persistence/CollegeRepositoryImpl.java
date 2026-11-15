package com.wlf.user.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.user.domain.college.College;
import com.wlf.user.domain.college.CollegeRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 学院读取端口的 MyBatis-Plus 实现。
 */
@Repository
public class CollegeRepositoryImpl implements CollegeRepository {

    private final CollegeMapper collegeMapper;

    public CollegeRepositoryImpl(CollegeMapper collegeMapper) {
        this.collegeMapper = collegeMapper;
    }

    @Override
    public List<College> findAllOrderedById() {
        return collegeMapper.selectList(
                Wrappers.<College>lambdaQuery().orderByAsc(College::getId));
    }

    @Override
    public boolean existsById(Long id) {
        return id != null
                && collegeMapper.exists(Wrappers.<College>lambdaQuery().eq(College::getId, id));
    }
}
