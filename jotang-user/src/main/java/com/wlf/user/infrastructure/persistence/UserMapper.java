package com.wlf.user.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.user.domain.user.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * user 表的数据访问。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
