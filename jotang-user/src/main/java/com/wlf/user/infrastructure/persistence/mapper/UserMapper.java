package com.wlf.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.user.infrastructure.persistence.po.UserPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * user 表的数据访问。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {
}
