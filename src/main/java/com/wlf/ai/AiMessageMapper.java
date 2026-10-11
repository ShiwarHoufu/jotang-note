package com.wlf.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * ai_message 表的数据访问。
 *
 * <p>单表 CRUD 由 {@link BaseMapper} 提供
 * 在 Service 里用 {@code Wrappers.lambdaQuery()} 拼即可。
 */
@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {
}
