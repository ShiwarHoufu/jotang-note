package com.wlf.user.domain.notification;

/**
 * 发信端口：只提供「发一封邮件」这一个原语，主题与正文由调用方组装。
 */
public interface MailService {

    /**
     * 发一封纯文本邮件。
     *
     * @param to       收件人邮箱
     * @param subject  主题
     * @param textBody 正文（纯文本，不带 HTML）
     */
    void send(String to, String subject, String textBody);
}
