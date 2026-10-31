package com.wlf.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * user 表的数据访问。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
