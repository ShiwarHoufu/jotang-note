package com.wlf.mail;

/**
 * 发信这件事的唯一抽象。见《概要设计》§6.6。
 *
 * <p><b>形状照 {@link com.wlf.storage.StorageService}</b>
 * 本层只提供「发一封邮件」这一个原语，主题与正文由调用方组装。
 */
public interface MailService {

    /**
     * 发一封纯文本邮件。
     *
     * @param to        收件人邮箱
     * @param subject   主题
     * @param textBody  正文（纯文本，不带 HTML——正文里含高熵令牌，纯文本能少一层转义面）
     */
    void send(String to, String subject, String textBody);
}
