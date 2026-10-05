package com.wlf.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * user 表的数据访问。
 *
 * <p>单表 CRUD 由 {@link BaseMapper} 提供，不写 XML。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
