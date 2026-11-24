package com.wlf.user.application.service;

import com.wlf.user.application.result.CollegeResult;
import com.wlf.user.domain.college.CollegeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 学院查询。
 *
 * <p>只有读：学院是预置数据，用户不能自建。
 */
@Service
public class CollegeService {

    private final CollegeRepository collegeRepository;

    public CollegeService(CollegeRepository collegeRepository) {
        this.collegeRepository = collegeRepository;
    }

    /**
     * 全部学院。不分页、不过滤。
     */
    public List<CollegeResult> list() {
        return collegeRepository.findAllOrderedById().stream()
                .map(CollegeResult::from)
                .toList();
    }

    /**
     * 学院是否存在。
     *
     * @param id 允许为 null（此时返回 false）
     */
    public boolean exists(Long id) {
        return collegeRepository.existsById(id);
    }
}
