package com.wlf.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.user.infrastructure.persistence.po.PasswordResetTokenPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * password_reset_token 表的数据访问。
 *
 * <p>时间一律由应用传入，签发与消费使用同一个 {@code now}。
 */
@Mapper
public interface PasswordResetTokenMapper extends BaseMapper<PasswordResetTokenPO> {

    @Insert("""
            INSERT INTO password_reset_token (user_id, token_hash, expires_at)
            VALUES (#{userId}, #{tokenHash}, #{expiresAt})
            """)
    int insertToken(@Param("userId") Long userId,
                    @Param("tokenHash") String tokenHash,
                    @Param("expiresAt") LocalDateTime expiresAt);

    /**
     * 一次性消费。
     *
     * @return 受影响行数。<b>1 = 本次消费成功；0 = 令牌不存在、已用过或已过期</b>
     */
    @Update("""
            UPDATE password_reset_token
            SET used_at = #{now}
            WHERE token_hash = #{tokenHash}
              AND used_at IS NULL
              AND expires_at > #{now}
            """)
    int consume(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    @Update("UPDATE password_reset_token SET used_at = #{now} WHERE user_id = #{userId} AND used_at IS NULL")
    int invalidateAllUnused(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("""
            SELECT id, user_id AS userId, token_hash AS tokenHash,
                   expires_at AS expiresAt, used_at AS usedAt, created_at AS createdAt
            FROM password_reset_token
            WHERE token_hash = #{tokenHash}
            """)
    PasswordResetTokenPO selectByTokenHash(@Param("tokenHash") String tokenHash);
}
