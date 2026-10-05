package com.wlf.catalog;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.entity.College;
import org.apache.ibatis.annotations.Mapper;

/**
 * college 表的数据访问。
 *
 * <p>单表 CRUD 由 {@link BaseMapper} 提供，不写 XML。
 */
@Mapper
public interface CollegeMapper extends BaseMapper<College> {
}
