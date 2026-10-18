package com.wlf.auth;

/**
 * 重置邮件的主题、链接与正文。
 */
public final class PasswordResetMailContent {

    /**
     * 主题
     */
    public static final String SUBJECT = "【Jotang Note】重置你的登录密码";

    private PasswordResetMailContent() {
    }

    /**
     * 拼出重置链接
     *
     * @param base     前端重置页地址，来自 {@code jotang.mail.reset-link-base}
     * @param rawToken 原始令牌（明文），只出现在这封邮件里
     */
    public static String link(String base, String rawToken) {
        return base + "?token=" + rawToken;
    }

    /**
     * 正文
     */
    public static String body(String link, long ttlMinutes) {
        return """
                你好，

                我们收到了重置 Jotang Note 登录密码的请求。请打开下面的链接设置新密码：

                %s

                链接 %d 分钟内有效，且只能使用一次。
                如果不是你本人操作，忽略这封邮件即可，你的密码不会被改动。

                Jotang Note
                """.formatted(link, ttlMinutes);
    }
}
