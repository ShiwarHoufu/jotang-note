package com.wlf.mail;

/**
 * 一条待发送的邮件，也就是消息的载荷。见《概要设计》§6.6。
 *
 * <p><b>⚠ 正文里含明文令牌，而它现在也进了 Broker。</b>这是「异步化」与
 * 「只存 SHA-256、明文只出现在邮件链接里」之间真实存在的张力。缓解手段有三层
 * （消息不持久化、工作队列 10 分钟 TTL、死信队列 1 小时 TTL），残余风险记在 §8.4。
 */
public record MailDispatch(String messageId, String to, String subject, String textBody) {
}
