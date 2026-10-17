package com.wlf.mail;

/**
 * 一条待发送的邮件，也就是消息的载荷。
 */
public record MailDispatch(String messageId, String to, String subject, String textBody) {
}
