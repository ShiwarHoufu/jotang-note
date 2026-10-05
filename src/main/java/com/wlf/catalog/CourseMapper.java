package com.wlf.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.entity.Course;
import org.apache.ibatis.annotations.Mapper;

/**
 * course 表的数据访问。
 *
 * <p>单表 CRUD 由 {@link BaseMapper} 提供，不写 XML。
 *
 * <p>按名称 / 学院搜索（§5.3）不落在这里：那是条件不固定的动态查询，
 * 用 {@code Wrappers.lambdaQuery()} 在 Service 里拼，Mapper 保持零方法。
 */
@Mapper
public interface CourseMapper extends BaseMapper<Course> {
}
