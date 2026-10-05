package com.wlf.catalog;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wlf.catalog.dto.CollegeResponse;
import com.wlf.entity.College;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 学院查询。见《概要设计》§5.3。
 *
 * <p>只有读：学院是预置数据，用户不能自建。
 *
 * <p>注册时校验学院是否存在的入口也在这里（auth 模块跨模块走 Service，不直接引用
 * {@link CollegeMapper}，见《概要设计》§1.1）。
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
     * <p>不分页、不过滤：学院是预置的几十条数据，前端整份拉走做下拉框。
     * 按 id 升序（即初始化脚本的录入顺序）——不能按 name 排，
     * 默认排序规则对中文按 Unicode 码点而非拼音排序，排了没有意义。
     */
    public List<CollegeResponse> list() {
        return collegeMapper.selectList(
                        Wrappers.<College>lambdaQuery().orderByAsc(College::getId))
                .stream()
                .map(CollegeResponse::from)
                .toList();
    }

    /**
     * 学院是否存在。供 auth 注册时校验入参（跨模块走 Service，§1.1）。
     *
     * <p>为什么不直接靠 {@code user.college_id} 的外键：外键确实拦得住非法值，
     * 但它抛的是 {@code DataIntegrityViolationException}，会被 GlobalExceptionHandler
     * 兜底成 50000「服务器内部错误」——而传了个不存在的学院明明是**客户端的参数错误**。
     * 所以这里先查一次，好给出 40001 加字段级明细。
     *
     * @param id 允许为 null（此时返回 false），免得调用方还要自己判空
     */
    public boolean exists(Long id) {
        return id != null
                && collegeMapper.exists(Wrappers.<College>lambdaQuery().eq(College::getId, id));
    }
}
