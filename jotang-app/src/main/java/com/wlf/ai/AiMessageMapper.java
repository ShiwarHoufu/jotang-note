package com.wlf.ai;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * ai_message 表的数据访问。
 *
 * <p>单表 CRUD 由 {@link BaseMapper} 提供
 */
@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {

    /**
     * 某用户最近一次对话所在的会话 id。
     *
     * @return 会话 id；该用户一条消息都没有时为 null，调用方据此回空载荷
     */
    @Select("""
            SELECT conversation_id FROM ai_message
            WHERE user_id = #{userId}
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    String selectLatestConversationId(@Param("userId") Long userId);
}
