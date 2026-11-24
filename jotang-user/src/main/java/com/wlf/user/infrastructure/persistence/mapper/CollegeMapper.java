package com.wlf.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.user.infrastructure.persistence.po.CollegePO;
import org.apache.ibatis.annotations.Mapper;

/**
 * college 表的数据访问。
 */
@Mapper
public interface CollegeMapper extends BaseMapper<CollegePO> {
}
