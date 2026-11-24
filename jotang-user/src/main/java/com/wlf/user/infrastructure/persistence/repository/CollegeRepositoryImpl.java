package com.wlf.user.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.user.domain.college.College;
import com.wlf.user.domain.college.CollegeRepository;
import com.wlf.user.infrastructure.persistence.converter.CollegeConverter;
import com.wlf.user.infrastructure.persistence.mapper.CollegeMapper;
import com.wlf.user.infrastructure.persistence.po.CollegePO;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 学院读取端口的 MyBatis-Plus 实现。
 */
@Repository
public class CollegeRepositoryImpl implements CollegeRepository {

    private final CollegeMapper collegeMapper;
    private final CollegeConverter converter;

    public CollegeRepositoryImpl(CollegeMapper collegeMapper, CollegeConverter converter) {
        this.collegeMapper = collegeMapper;
        this.converter = converter;
    }

    @Override
    public List<College> findAllOrderedById() {
        return collegeMapper.selectList(
                        Wrappers.<CollegePO>lambdaQuery().orderByAsc(CollegePO::getId))
                .stream()
                .map(converter::toDomain)
                .toList();
    }

    @Override
    public boolean existsById(Long id) {
        return id != null
                && collegeMapper.exists(Wrappers.<CollegePO>lambdaQuery().eq(CollegePO::getId, id));
    }
}
