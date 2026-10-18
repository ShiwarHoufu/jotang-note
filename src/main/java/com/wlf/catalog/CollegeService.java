package com.wlf.catalog;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.dto.CollegeResponse;
import com.wlf.entity.College;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 学院查询。
 *
 * <p>只有读：学院是预置数据，用户不能自建。
 */
@Service
public class CollegeService {

    private final CollegeMapper collegeMapper;

    public CollegeService(CollegeMapper collegeMapper) {
        this.collegeMapper = collegeMapper;
    }

    /**
     * 全部学院。
     *
     * <p>不分页、不过滤
     */
    public List<CollegeResponse> list() {
        return collegeMapper.selectList(
                        Wrappers.<College>lambdaQuery().orderByAsc(College::getId))
                .stream()
                .map(CollegeResponse::from)
                .toList();
    }

    /**
     * 学院是否存在。
     *
     * @param id 允许为 null（此时返回 false）
     */
    public boolean exists(Long id) {
        return id != null
                && collegeMapper.exists(Wrappers.<College>lambdaQuery().eq(College::getId, id));
    }
}
