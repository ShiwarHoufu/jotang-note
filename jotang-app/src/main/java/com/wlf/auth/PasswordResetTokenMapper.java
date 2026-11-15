package com.wlf.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wlf.entity.PasswordResetToken;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * password_reset_token 表的数据访问。
 *
 * <p>三条语句共用一个前提：时钟一律取数据库的</p>
 */
@Mapper
public interface PasswordResetTokenMapper extends BaseMapper<PasswordResetToken> {

    /**
     * 签发一枚令牌。
     *
     * @param ttlMinutes 有效期（分钟）
     * @return 受影响行数，恒为 1
     */
    @Insert("""
            INSERT INTO password_reset_token (user_id, token_hash, expires_at)
            VALUES (#{userId}, #{tokenHash}, TIMESTAMPADD(MINUTE, #{ttlMinutes}, NOW()))
            """)
    int insertToken(@Param("userId") Long userId,
                    @Param("tokenHash") String tokenHash,
                    @Param("ttlMinutes") long ttlMinutes);

    /**
     * 一次性消费
     *
     * @return 受影响行数。<b>1 = 本次消费成功；0 = 令牌不存在、已用过或已过期</b>
     */
    @Update("""
            UPDATE password_reset_token
            SET used_at = NOW()
            WHERE token_hash = #{tokenHash}
              AND used_at IS NULL
              AND expires_at > NOW()
            """)
    int consume(@Param("tokenHash") String tokenHash);

    /**
     * 作废某个用户当前全部未消费的令牌。
     * 两个调用点：重新申请时、改密成功后。
     */
    @Update("UPDATE password_reset_token SET used_at = NOW() WHERE user_id = #{userId} AND used_at IS NULL")
    int invalidateAllUnused(@Param("userId") Long userId);

    /**
     * 按令牌哈希查它属于谁。
     */
    @Select("SELECT user_id FROM password_reset_token WHERE token_hash = #{tokenHash}")
    Long selectUserIdByTokenHash(@Param("tokenHash") String tokenHash);
}
