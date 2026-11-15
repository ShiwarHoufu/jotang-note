package com.wlf.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * password_reset_token 表。只存 SHA-256 哈希，used_at 非空表示已消费。
 * 见《概要设计》§3.2、§6.6。
 *
 * <p><b>这一行里永远没有明文令牌。</b>列名叫 {@code token_hash} 而不是 {@code token}，
 * 是因为邮件链接里那个原始令牌只存在于「生成它的那一瞬间」和「用户收到的邮件」两处——
 * 库被整个读走也换不回一个可用的令牌。这也是它只需要 SHA-256、不需要 BCrypt 的原因（§6.6）：
 * 32 字节随机数的搜索空间让「离线爆破」不成立，BCrypt 的慢只会拖慢每次校验。
 *
 * <p>表名不加反引号（与 {@link User} 不同）：{@code password_reset_token} 不是 MySQL 关键字。
 */
@Getter
@Setter
@TableName("password_reset_token")
public class PasswordResetToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 外键指向 {@code user.id}（fk_prt_user） */
    private Long userId;

    /** SHA-256(原始令牌) 的小写 hex，64 字符，恰好填满 CHAR(64) */
    private String tokenHash;

    /** 签发时刻 + TTL，由数据库时钟写入（见 PasswordResetTokenMapper#insertToken） */
    private LocalDateTime expiresAt;

    /** 非空 = 已消费，一次性判据（§6.6 的唯一判据） */
    private LocalDateTime usedAt;

    private LocalDateTime createdAt;
}
