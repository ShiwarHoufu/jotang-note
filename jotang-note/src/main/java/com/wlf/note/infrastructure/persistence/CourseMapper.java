package com.wlf.note.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.note.infrastructure.persistence.po.CoursePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * course 表的数据访问。
 *
 * <p>单表 CRUD 由 {@link BaseMapper} 提供，不写 XML。
 */
@Mapper
public interface CourseMapper extends BaseMapper<CoursePO> {
}
