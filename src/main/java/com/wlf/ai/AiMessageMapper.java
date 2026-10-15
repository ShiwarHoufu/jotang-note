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
 * 在 Service 里用 {@code Wrappers.lambdaQuery()} 拼即可。
 */
@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {

    /**
     * 某用户最近一次对话所在的会话 id，用来把刷新后的前端接回那段会话。
     *
     * <p><b>只投影一个列</b>：整行取回来的话，会为拿一个 36 字符的 id 把该行的
     * {@code TEXT content}（最长 2000 字）一并读进来。与 {@code NoteMapper#selectFavoriteCount}
     * 同一条理由。
     *
     * <p>{@code created_at} 是 {@code DATETIME} 秒精度，同一轮问答的两行必然同秒，
     * 所以 {@code id} 这个次级排序不能省——没有它，「最近」在秒内是不确定的。
     * 两列都倒序，正好走 {@code idx_user_created (user_id, created_at)} 的反向扫描，无 filesort。
     *
     * @return 会话 id；<b>该用户一条消息都没有时为 {@code null}</b>——调用方据此回空载荷
     */
    @Select("""
            SELECT conversation_id FROM ai_message
            WHERE user_id = #{userId}
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """)
    String selectLatestConversationId(@Param("userId") Long userId);
}
