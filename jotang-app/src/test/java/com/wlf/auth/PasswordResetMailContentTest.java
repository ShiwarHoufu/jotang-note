package com.wlf.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PasswordResetMailContent} 的单元测试：纯函数，不起 Spring 上下文，也不发信。
 *
 * <p>要钉住的是「用户拿到的那封信里到底有什么」——链接里必须有那个明文令牌，
 * 正文里必须说清有效期。这两件事<b>错了不会报错，只会让用户点不开链接</b>，
 * 而且是在真实用户手里才发现。
 */
class PasswordResetMailContentTest {

    private static final String BASE = "http://localhost:5173/reset-password";
    private static final String TOKEN = "AbC-123_xyz";

    @Test
    void linkCarriesTheRawTokenAsAQueryParameter() {
        assertThat(PasswordResetMailContent.link(BASE, TOKEN))
                .isEqualTo(BASE + "?token=" + TOKEN);
    }

    /**
     * 令牌是 URL-safe Base64（字母表 {@code A-Za-z0-9-_}，无补位 {@code =}），
     * 所以链接里<b>不需要也不应该</b>做 URL 编码。这条用一个含 {@code -} 与 {@code _} 的
     * 令牌把「原样拼接」钉住：若哪天有人顺手加一层 {@code URLEncoder.encode}，
     * 这两个字符会被编码、而服务端收到的令牌就对不上哈希了。
     */
    @Test
    void linkDoesNotEncodeUrlSafeBase64Characters() {
        String link = PasswordResetMailContent.link(BASE, TOKEN);

        assertThat(link).contains(TOKEN).doesNotContain("%2D").doesNotContain("%5F");
    }

    @Test
    void bodyContainsTheLinkAndTheTtl() {
        String link = PasswordResetMailContent.link(BASE, TOKEN);

        assertThat(PasswordResetMailContent.body(link, 30))
                .contains(link)
                .contains("30 分钟")
                .contains("只能使用一次");
    }

    @Test
    void subjectIsNotBlank() {
        assertThat(PasswordResetMailContent.SUBJECT).isNotBlank();
    }
}
